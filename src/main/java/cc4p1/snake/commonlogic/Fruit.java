/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cc4p1.snake.commonlogic;

/**
 *
 * @author Albert
 */
public class Fruit {
    public int score;
    public Pt point;
    public Fruit(Pt point, int fruitScore)
    {
        this.point = point;
        this.score = fruitScore; 
    }
    
    @Override
    public String toString() {
        return "Fruit at " + point + " worth " + score;
    }
}
