import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.management.*;

public class LoadTester {

  // ---------- Bot definition ----------
  static class Bot implements Runnable {
    final String host;
    final int port;
    final int id;
    final int rate; // inputs per second
    final boolean readLoop;
    final AtomicBoolean running = new AtomicBoolean(true);
    final Random rnd = new Random();

    volatile Socket sock;
    volatile PrintWriter out;

    // NEW: callbacks so main can count real connections and alive bots
    final Runnable onConnected;   // called after connect()+JOIN
    final Runnable onStopped;     // called when run() exits

    Bot(String host, int port, int id, int rate, boolean readLoop,
        Runnable onConnected, Runnable onStopped) {
      this.host = host; this.port = port; this.id = id; this.rate = rate; this.readLoop = readLoop;
      this.onConnected = onConnected; this.onStopped = onStopped;
    }

    @Override public void run() {
      try (Socket s = new Socket()) {
        this.sock = s;
        s.setTcpNoDelay(true);
        s.connect(new InetSocketAddress(host, port), 3000);
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())), true);
        out.println("JOIN bot"+id);

        // NEW: mark a real connection only after connect+JOIN succeed
        if (onConnected != null) onConnected.run();

        Thread reader = null;
        if (readLoop) {
          reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
              while (running.get() && br.readLine() != null) { /* discard to avoid backpressure */ }
            } catch (IOException ignored) {}
          }, "bot-reader-"+id);
          reader.setDaemon(true);
          reader.start();
        }

        long periodNs = (rate > 0) ? 1_000_000_000L / rate : 100_000_000L;
        long next = System.nanoTime();
        String[] dirs = {"UP","DOWN","LEFT","RIGHT"};
        int di = id % dirs.length;

        while (running.get()) {
          out.println("INPUT " + dirs[di]);
          di = (di + 1 + rnd.nextInt(3)) % dirs.length; // wander
          next += periodNs;
          long sleepNs = next - System.nanoTime();
          if (sleepNs > 0) TimeUnit.NANOSECONDS.sleep(sleepNs);
        }
      } catch (IOException | InterruptedException ignored) {
      } finally {
        running.set(false);
        try { if (sock != null) sock.close(); } catch (IOException ignored2) {}
        if (onStopped != null) onStopped.run();
      }
    }

    void stop() { running.set(false); }
    boolean alive() { return running.get(); }
  }

  // ---------- Metrics helpers ----------
  static class CpuSampler {
    private final OperatingSystemMXBean stdOs = ManagementFactory.getOperatingSystemMXBean();
    private final com.sun.management.OperatingSystemMXBean sunOs =
        (stdOs instanceof com.sun.management.OperatingSystemMXBean)
            ? (com.sun.management.OperatingSystemMXBean) stdOs : null;
    private final int cores = Runtime.getRuntime().availableProcessors();
    private long lastCpuTime = -1;
    private long lastWallNanos = -1;

    // Returns process CPU % [0..100], system CPU % [0..100] where available.
    double[] sample() {
      double proc = Double.NaN, sys = Double.NaN;
      long now = System.nanoTime();
      if (sunOs != null) {
        @SuppressWarnings("deprecation")
        double p = sunOs.getProcessCpuLoad();   // 0..1 or <0 if NA
        @SuppressWarnings("deprecation")
        double s = sunOs.getSystemCpuLoad();    // 0..1 or <0 if NA
        if (p >= 0) proc = p * 100.0;
        if (s >= 0) sys  = s * 100.0;
        if (Double.isNaN(proc)) {
          long cput = sunOs.getProcessCpuTime(); // nanos
          if (lastCpuTime >= 0 && lastWallNanos >= 0) {
            long dCpu = cput - lastCpuTime;
            long dWall = now - lastWallNanos;
            if (dWall > 0) proc = (dCpu * 100.0) / (dWall * cores);
          }
          lastCpuTime = cput;
          lastWallNanos = now;
        }
      }
      return new double[]{proc, sys};
    }
  }

  static class MemSampler {
    private final MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
    long gcCount() { long c=0; for (GarbageCollectorMXBean b: gcs) { long v=b.getCollectionCount(); if (v>0) c+=v; } return c; }
    long gcTimeMs() { long t=0; for (GarbageCollectorMXBean b: gcs) { long v=b.getCollectionTime(); if (v>0) t+=v; } return t; }
    long heapUsed() { return mem.getHeapMemoryUsage().getUsed(); }
    long heapCommitted() { return mem.getHeapMemoryUsage().getCommitted(); }
    long nonHeapUsed() { return mem.getNonHeapMemoryUsage().getUsed(); }
  }

  // ---------- CSV logger ----------
  static class CsvLog implements Closeable {
    private final PrintWriter pw;
    CsvLog(String path, String header) throws IOException {
      pw = new PrintWriter(new BufferedWriter(new FileWriter(path, false)));
      pw.println(header);
      pw.flush();
    }
    void row(Object... cells) {
      StringBuilder sb = new StringBuilder();
      for (int i=0;i<cells.length;i++) {
        if (i>0) sb.append(',');
        sb.append(escape(String.valueOf(cells[i])));
      }
      pw.println(sb.toString());
      pw.flush();
    }
    private String escape(String s){
      if (s.indexOf(',')>=0 || s.indexOf('"')>=0 || s.indexOf('\n')>=0){
        return "\""+s.replace("\"","\"\"")+"\"";
      }
      return s;
    }
    @Override public void close(){ pw.close(); }
  }

  public static void main(String[] args) throws Exception {
    // Defaults
    String host = "127.0.0.1";
    int port = 5000;
    int clients = 100;
    int rate = 10;          // inputs/sec per client
    int duration = 60;      // seconds total test time (after ramp starts)
    boolean read = false;   // whether bots read server STATEs
    int rampMs = 10;        // ms between launching bots
    int sampleMs = 1000;    // metrics sample period
    String logPath = "load_results.csv";

    // Args
    for (int i=0; i<args.length; i++) {
      switch (args[i]) {
        case "--host": host = args[++i]; break;
        case "--port": port = Integer.parseInt(args[++i]); break;
        case "--clients": clients = Integer.parseInt(args[++i]); break;
        case "--rate": rate = Integer.parseInt(args[++i]); break;
        case "--duration": duration = Integer.parseInt(args[++i]); break;
        case "--read": read = Boolean.parseBoolean(args[++i]); break;
        case "--rampMs": rampMs = Integer.parseInt(args[++i]); break;
        case "--sampleMs": sampleMs = Integer.parseInt(args[++i]); break;
        case "--log": logPath = args[++i]; break;
      }
    }

    System.out.printf(
      "LoadTester start: host=%s port=%d clients=%d rate=%d/s duration=%ds read=%s rampMs=%d sampleMs=%d log=%s%n",
      host, port, clients, rate, duration, read, rampMs, sampleMs, logPath
    );

    final ExecutorService pool = Executors.newCachedThreadPool();
    final List<Bot> bots = new ArrayList<>(clients);

    // NEW: separate counters for better CSV visibility during ramp
    final AtomicInteger submitted = new AtomicInteger(0);   // tasks submitted
    final AtomicInteger connected = new AtomicInteger(0);   // sockets connected + JOIN sent
    final AtomicInteger alive = new AtomicInteger(0);       // bots currently running
    final AtomicBoolean testRunning = new AtomicBoolean(true);

    // CSV header updated to include submitted/connected
    String header =
      "timestamp,elapsed_s,clients_target,clients_submitted,clients_connected,clients_alive,per_client_rate,total_input_rate," +
      "read_mode,ramp_ms,sample_ms,cpu_process_pct,cpu_system_pct,heap_used_mb,heap_committed_mb,nonheap_used_mb," +
      "gc_count,gc_time_ms,threads";
    CsvLog log = new CsvLog(logPath, header);
    Instant t0 = Instant.now();
    DateTimeFormatter tsFmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    final int clients0   = clients;
    final int rate0      = rate;
    final boolean read0  = read;
    final int rampMs0    = rampMs;
    final int sampleMs0  = sampleMs;

    // Start sampler BEFORE ramp so CSV shows the ramp in real time
Thread sampler = new Thread(() -> {
  CpuSampler cpu = new CpuSampler();
  MemSampler mem = new MemSampler();
  ThreadMXBean tbean = ManagementFactory.getThreadMXBean();
  try {
    while (testRunning.get()) {
      long elapsedMs = Duration.between(t0, Instant.now()).toMillis();
      double[] cpuVals = cpu.sample();
      double cpuProc = round2(cpuVals[0]);
      double cpuSys  = round2(cpuVals[1]);
      int aliveNow = alive.get();
      int connNow  = connected.get();
      int subNow   = submitted.get();
      long totalRate = (long) aliveNow * rate0;   // <-- use rate0

      log.row(
        OffsetDateTime.now().format(tsFmt),
        toSec(elapsedMs),
        clients0,                                  // <-- use clients0
        subNow,
        connNow,
        aliveNow,
        rate0,                                     // <-- use rate0
        totalRate,
        read0,                                     // <-- use read0
        rampMs0,                                   // <-- use rampMs0
        sampleMs0,                                 // <-- use sampleMs0
        safe(cpuProc),
        safe(cpuSys),
        mem.heapUsed()/(1024*1024),
        mem.heapCommitted()/(1024*1024),
        mem.nonHeapUsed()/(1024*1024),
        mem.gcCount(),
        mem.gcTimeMs(),
        tbean.getThreadCount()
      );

      Thread.sleep(sampleMs0);                      // <-- use sampleMs0
    }
  } catch (InterruptedException ignore) {
  } finally {
    try { log.close(); } catch (Exception ignore) {}
  }
}, "sampler");
    sampler.setDaemon(true);
    sampler.start();

    // Ramp up bots (sampler runs concurrently)
    for (int i=0; i<clients; i++) {
      Bot b = new Bot(
        host, port, i+1, rate, read,
        () -> { connected.incrementAndGet(); alive.incrementAndGet(); }, // onConnected
        () -> { alive.decrementAndGet(); }                               // onStopped
      );
      bots.add(b);
      submitted.incrementAndGet();
      pool.execute(b);
      if (rampMs > 0) Thread.sleep(rampMs);
    }

    // Let test run for 'duration' seconds (sampler continues)
    Thread.sleep(duration * 1000L);

    // Stop everything
    testRunning.set(false);
    for (Bot b : bots) b.stop();
    pool.shutdownNow();
    sampler.interrupt();
    pool.awaitTermination(5, TimeUnit.SECONDS);
    System.out.println("Load finished. Results written to: " + logPath);
  }

  private static double round2(double v) {
    if (Double.isNaN(v)) return Double.NaN;
    return Math.round(v * 100.0) / 100.0;
  }
  private static String safe(double v) {
    return Double.isNaN(v) ? "" : String.valueOf(v);
  }
  private static String toSec(long millis) {
    return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
  }
}
