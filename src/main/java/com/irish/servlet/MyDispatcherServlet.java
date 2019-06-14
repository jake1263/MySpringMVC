package com.irish.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.irish.annotation.MyController;
import com.irish.annotation.MyRequestMapping;
import com.irish.annotation.MyRequestParam;

public class MyDispatcherServlet extends HttpServlet{

	private static final long serialVersionUID = 1L;

	private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();

    private Map<String, Method> handlerMapping = new  HashMap<>();

    private Map<String, Object> controllerMap  =new HashMap<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            //处理请求
            doDispatch(req,resp);
        } catch (Exception e) {
            resp.getWriter().write("500!! Server Exception");
        }

    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if(handlerMapping.isEmpty()){
            return;
        }
        String url =req.getRequestURI();
        String contextPath = req.getContextPath();
        System.out.println("requestUri:"+url);
        System.out.println("contextPath:"+contextPath);
        url=url.replace(contextPath, "").replaceAll("/+", "/");
        System.out.println("url:"+url);
        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 NOT FOUND!");
            return;
        }
        
        Method method =this.handlerMapping.get(url);
        
        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //获取方法的参数列表的注解值，顺序和长度与parameterTypes相同，
        String [] paramNames = getMethodParameterNamesByAnnotation(method);
        
        //获取请求的参数
        Map<String, String[]> parameterMap = req.getParameterMap();
       
        //保存参数值
        Object [] paramValues= new Object[parameterTypes.length];
        
        //方法的参数列表
        for (int i = 0; i<parameterTypes.length; i++){  
            //根据参数名称，做某些处理  
            String requestParam = parameterTypes[i].getSimpleName();  
            if (requestParam.equals("HttpServletRequest")){  
                //参数类型已明确，这边强转类型  
                paramValues[i]=req;
                continue;  
            }  
            if (requestParam.equals("HttpServletResponse")){  
                paramValues[i]=resp;
                continue;  
            }
            if(requestParam.equals("String")){
            	String theParamName = paramNames[i];
                for (Entry<String, String[]> param : parameterMap.entrySet()) {
                		if(param.getKey().equals(theParamName)) {
                			 String value =Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                             //[abc, ed]  替换为  abc,ed
                             paramValues[i]=value;
                             System.out.println(theParamName  + " : "+value);
                		} 
                }
            }
        }  
        //利用反射机制来调用
        try {
            method.invoke(this.controllerMap.get(url), paramValues);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    
    /**
     * 
     * 按照方法参数的顺序获取注解的值，没有注解的参数对应的位置为空
     */
    public static String[] getMethodParameterNamesByAnnotation(Method method) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        if (parameterAnnotations == null || parameterAnnotations.length == 0) {
            return null;
        }
        String[] parameterNames = new String[parameterAnnotations.length];
        int i = 0;
        for (Annotation[] parameterAnnotation : parameterAnnotations) {
        	if(parameterAnnotation.length == 0 ) {
        		  parameterNames[i++] = null;
        	}
            for (Annotation annotation : parameterAnnotation) {
                if (annotation instanceof MyRequestParam) {
                	MyRequestParam param = (MyRequestParam) annotation;
                    parameterNames[i++] = param.value();
                }
            }
        }
        return parameterNames;
    }

    
    @Override
    public void init(ServletConfig config) throws ServletException {

        //1.加载配置文件，将classpath路径下的application.properties文件加载到内存
    	//config.getInitParameter("contextConfigLocation") 获取的是Servlet的配置参数
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2.将指定路径下的全局限定类名添加到classNames集合当中
        doScanner(properties.getProperty("scanPackage"));

        //3.通过反射实例化标注了MyController注解的类,并且放到ioc容器中
        doInstance();

        //4.初始化url和Method的对应关系，url和Object的对应关系
        initHandlerMapping();

    }
    
    private void  doLoadConfig(String location){
        //把web.xml中的contextConfigLocation对应value值的文件加载到流里面
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            //用Properties文件加载文件里的内容
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            //关流
            if(null!=resourceAsStream){
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void doScanner(String packageName) {
        //把所有的.替换成/
        URL url  =this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            if(file.isDirectory()){
                //递归读取包
                doScanner(packageName+"."+file.getName());
            }else{
                String className =packageName +"." +file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }

    
    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }   
        for (String className : classNames) {
            try {
                //把类搞出来,反射来实例化(只有加@MyController需要实例化)
                Class<?> clazz =Class.forName(className);
                if(clazz.isAnnotationPresent(MyController.class)){
                    ioc.put(toLowerFirstWord(clazz.getSimpleName()),clazz.newInstance());
                }else{
                    continue;
                }

            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }
    }

    private void initHandlerMapping(){
        if(ioc.isEmpty()){
            return;
        }
        try {
            for(Entry<String, Object> entry: ioc.entrySet()) {
                Class<? extends Object> clazz = entry.getValue().getClass();
                if(!clazz.isAnnotationPresent(MyController.class)){
                    continue;
                }
                //拼url时,是controller头部的url拼上方法上的url
                String baseUrl ="";
                if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                    MyRequestMapping annotation = clazz.getAnnotation(MyRequestMapping.class);
                    baseUrl=annotation.value();
                }
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if(!method.isAnnotationPresent(MyRequestMapping.class)){
                        continue;
                    }
                    MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
                    String url = annotation.value();

                    url =(baseUrl+"/"+url).replaceAll("/+", "/");
                    handlerMapping.put(url,method);
                    controllerMap.put(url,clazz.newInstance());
                    System.out.println(url+","+method);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 字符串首字母小写
     */
    private String toLowerFirstWord(String name){
        char[] charArray = name.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }
    
    
}