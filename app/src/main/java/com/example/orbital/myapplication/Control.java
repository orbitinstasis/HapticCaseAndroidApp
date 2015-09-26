package com.example.orbital.myapplication;

/**
 * Created by Orbital on 26/09/2015.
 */
public class Control {


    protected long map(long x, long in_min, long in_max, long out_min, long out_max)
    {
        if (x > in_max) x = in_max;
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

}
