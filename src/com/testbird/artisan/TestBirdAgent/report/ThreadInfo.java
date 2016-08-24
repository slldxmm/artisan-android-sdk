/*
 * Copyright (C) 2016 TestBird  - All Rights Reserved
 * You may use, distribute and modify this code under
 * the terms of the mit license.
 */
package com.testbird.artisan.TestBirdAgent.report;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenxuesong on 15/11/26.
 */
class ThreadInfo {

    boolean crashed;
    String id;
    String name;
    String group;
    String state;
    String level;
    String stack;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("crashed", crashed);
        map.put("id" , id);
        map.put("name", name);
        map.put("group", group);
        map.put("state", state);
        map.put("level", level);
        map.put("stack", stack);
        return map;
    }
}
