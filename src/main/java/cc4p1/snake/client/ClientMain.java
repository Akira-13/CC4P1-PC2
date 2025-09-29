/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cc4p1.snake.client;

import cc4p1.snake.ui.GameWindowClient;

/**
 *
 * @author Albert
 */
public class ClientMain {
    public static void main(String[] args){
        java.awt.EventQueue.invokeLater(()->{
            GameWindowClient gameWindow = new GameWindowClient();
            gameWindow.setVisible(true);
        });
    }
}
