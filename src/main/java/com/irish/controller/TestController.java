package com.irish.controller;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.irish.annotation.MyController;
import com.irish.annotation.MyRequestMapping;
import com.irish.annotation.MyRequestParam;

@MyController
@MyRequestMapping("/test")
public class TestController {

    @MyRequestMapping("/doTest")
    public void test1(HttpServletRequest request, HttpServletResponse response,
            @MyRequestParam("param1") String param1 , @MyRequestParam("param2") String param2  ){
        System.out.println("param1 = "+ param1);
        System.out.println("param2 = "+ param2);
        try {
            response.getWriter().write( "doTest method success! param1:"+param1 + " , param2:"+ param2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

     @MyRequestMapping("/doTest2")
    public void test2(HttpServletRequest request, HttpServletResponse response){
        try {
            response.getWriter().println("doTest2 method success!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
