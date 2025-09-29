/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cc4p1.snake.commonlogic;

import java.util.LinkedList;

/**
 *
 * @author Albert
 */
public class Snake {
    //public int id;
    public String name;
    public int size = 1;
    public LinkedList<Pt> points;
    
    public Snake(String name, LinkedList<Pt> points){
        this.name = name;
        this.points = points;
    }
}

