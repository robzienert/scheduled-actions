/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.scheduledactions.persistence;

import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractInMemoryDao<T> {

    public static final String SEPARATOR = ":";

    private final ConcurrentMap<String, ConcurrentMap<String,T>> map = new ConcurrentHashMap<String, ConcurrentMap<String,T>>();
    private final String idSeparator;

    protected AbstractInMemoryDao() {
        ParameterizedType parameterizedType = (ParameterizedType) this.getClass().getGenericSuperclass();
        Class<T> parameterClass = (Class<T>) parameterizedType.getActualTypeArguments()[0];
        this.idSeparator = String.format("%s%s%s", SEPARATOR, parameterClass.getName(), SEPARATOR);
    }

    protected void create(String group, String id, T type) {
        ConcurrentMap<String, T> subMap = new ConcurrentHashMap<String, T>();
        subMap.put(id, type);
        Map existingTriggerMap = map.putIfAbsent(group, subMap);
        if (existingTriggerMap != null) {
            synchronized (map) {
                map.get(group).put(id, type);
            }
        }
    }

    protected void update(String group, String id, T type) {
        synchronized (map) {
            map.get(group).put(id, type);
        }
    }

    protected T read(String group, String id) {
        ConcurrentMap<String, T> subMap = map.get(group);
        for (Iterator<String> iterator2 = subMap.keySet().iterator(); iterator2.hasNext();) {
            String storedId = iterator2.next();
            if (id.equals(storedId)) {
                return subMap.get(id);
            }
        }
        return null;
    }

    protected void delete(String group, String id) {
        synchronized (map) {
            map.get(group).remove(id);
        }
    }

    protected List<T> list(String group, int count) {
        List<T> items = list(group);
        return items.size() > count ? items.subList(0, count) : items;
    }

    protected List<T> list(String group) {
        return map.get(group) != null ? new ArrayList<T>(map.get(group).values()) : new ArrayList<T>();
    }

    protected List<T> list() {
        List<T> items = new ArrayList<>();
        for (Iterator<String> iterator = map.keySet().iterator(); iterator.hasNext();) {
            items.addAll(map.get(iterator.next()).values());
        }
        return items;
    }

    protected boolean isIdFormat(String id) {
        return id.contains(idSeparator);
    }

    protected String createId(String group, String id) {
        if (group == null || id == null || group.contains(idSeparator) || id.contains(idSeparator)) {
            throw new IllegalArgumentException(String.format("Illegal arguments specified for column name creation (group = %s, id = %s)", group, id));
        }
        return String.format("%s%s%s", group, idSeparator, id);
    }

    protected String extractGroupFromId(String columnName) {
        if (columnName == null || !columnName.contains(idSeparator)) return columnName;
        String[] tokens = columnName.split(idSeparator);
        if (tokens.length == 2) {
            return tokens[0];
        } else {
            throw new IllegalArgumentException(String.format("Cannot extract row key from column name string: %s", columnName));
        }
    }
}

