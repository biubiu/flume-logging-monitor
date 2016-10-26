package com.shawn.logging.monitor.handler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public final class JsonFormatMapConfigHelper <K,T>{
    private JsonFormatMapConfigHelper(){}

    private static Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();

    private static final  Logger logger = LoggerFactory.getLogger(JsonFormatMapConfigHelper.class);

    public static <K, T>  void writeJsonMap(Map<K, T> map,String path,TypeToken<T> innerType){
        FileWriter writer;
            try {
                @SuppressWarnings("serial")
                Type type = new TypeToken<Map<K, T>>() {}.where(new TypeParameter<T>() {}, innerType).getType();
                writer = new FileWriter(new File(path));
                writer.append(gson.toJson(map,type));
                writer.close();
            } catch (IOException e) {
                logger.error("IO error when writing log status {}",Throwables.getStackTraceAsString(e));                
            }

    }

    public static <K, T> Map<K, T> loadJsonMap(Map<K,T> map,String filepath,TypeToken<T> innerType){
        map = Maps.newConcurrentMap();
        File file =new File(filepath);
        if(file.exists()){
            try {
                @SuppressWarnings("serial")
               Type type = new TypeToken<Map<K, T>>() {}.where(new TypeParameter<T>() {}, innerType).getType();
                map = gson.fromJson(new FileReader(file), type);
            } catch (JsonIOException | JsonSyntaxException|FileNotFoundException e) {
                logger.error("json io error {}",Throwables.getStackTraceAsString(e));
            }
        }
        return map;
    }
}
