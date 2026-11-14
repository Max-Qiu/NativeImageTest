package com.maxqiu.test.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

/** Minimal JavaBeans for property access, iteration and recursion syntax tests. */
public final class ThymeleafModels {
    private ThymeleafModels() {}

    @Getter
    @AllArgsConstructor
    public static class Value {
        private String value;
    }

    @Getter
    @AllArgsConstructor
    public static class NullableValue {
        private String value;
    }

    @Getter
    @AllArgsConstructor
    public static class Wrapper {
        private Value nested;
    }

    @Getter
    @AllArgsConstructor
    public static class Message implements Serializable {
        private static final long serialVersionUID = 1L;
        private String message;
    }

    @Getter
    @AllArgsConstructor
    public static class Item {
        private String code;
        private boolean enabled;
    }

    @Getter
    @AllArgsConstructor
    public static class Node {
        private String name;
        private List<Node> children;
    }
}
