/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.niluogege.duplicatedbitmapanalyzer;

import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Field;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;

public final class HahaHelper {

  private static final Set<String> WRAPPER_TYPES = new HashSet<>(
      asList(Boolean.class.getName(), Character.class.getName(), Float.class.getName(),
          Double.class.getName(), Byte.class.getName(), Short.class.getName(),
          Integer.class.getName(), Long.class.getName()));

  static String fieldToString(Map.Entry<Field, Object> entry) {
    return fieldToString(entry.getKey(), entry.getValue());
  }

  static String fieldToString(ClassInstance.FieldValue fieldValue) {
    return fieldToString(fieldValue.getField(), fieldValue.getValue());
  }

  static String fieldToString(Field field, Object value) {
    return field.getName() + " = " + value;
  }



  static boolean extendsThread(ClassObj clazz) {
    boolean extendsThread = false;
    ClassObj parentClass = clazz;
    while (parentClass.getSuperClassObj() != null) {
      if (clazz.getClassName().equals(Thread.class.getName())) {
        extendsThread = true;
        break;
      }
      parentClass = parentClass.getSuperClassObj();
    }
    return extendsThread;
  }



  public static boolean isPrimitiveWrapper(Object value) {
    if (!(value instanceof ClassInstance)) {
      return false;
    }
    return WRAPPER_TYPES.contains(((ClassInstance) value).getClassObj().getClassName());
  }

  public static boolean isPrimitiveOrWrapperArray(Object value) {
    if (!(value instanceof ArrayInstance)) {
      return false;
    }
    ArrayInstance arrayInstance = (ArrayInstance) value;
    if (arrayInstance.getArrayType() != Type.OBJECT) {
      return true;
    }
    return WRAPPER_TYPES.contains(arrayInstance.getClassObj().getClassName());
  }

  private static boolean isCharArray(Object value) {
    return value instanceof ArrayInstance && ((ArrayInstance) value).getArrayType() == Type.CHAR;
  }

  static List<ClassInstance.FieldValue> classInstanceValues(Instance instance) {
    ClassInstance classInstance = (ClassInstance) instance;
    return classInstance.getValues();
  }

  static <T> T fieldValue(List<ClassInstance.FieldValue> values, String fieldName) {
    for (ClassInstance.FieldValue fieldValue : values) {
      if (fieldValue.getField().getName().equals(fieldName)) {
        //noinspection unchecked
        return (T) fieldValue.getValue();
      }
    }
    throw new IllegalArgumentException("Field " + fieldName + " does not exists");
  }

  static boolean hasField(List<ClassInstance.FieldValue> values, String fieldName) {
    for (ClassInstance.FieldValue fieldValue : values) {
      if (fieldValue.getField().getName().equals(fieldName)) {
        //noinspection unchecked
        return true;
      }
    }
    return false;
  }

  private HahaHelper() {
    throw new AssertionError();
  }
}