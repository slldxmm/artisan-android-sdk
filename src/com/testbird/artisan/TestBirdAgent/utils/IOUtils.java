/*
 * Copyright (C) 2016 TestBird  - All Rights Reserved
 * You may use, distribute and modify this code under
 * the terms of the mit license.
 */
package com.testbird.artisan.TestBirdAgent.utils;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by chenxuesong on 16/2/26.
 */
public class IOUtils {

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
