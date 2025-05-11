package com.gmail.coldrain608.lox;

import java.util.*;

public class Environment {

    public final Environment enclosing;
    private final List<VariableEntry> values = new ArrayList<>();

    static class VariableEntry {
        String key;
        Object var;

        VariableEntry(String key, Object var) {
            this.key = key;
            this.var = var;
        }
    }

    public Environment() {
        enclosing = null;
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    // 新增部分开始
    Object get(Token name) {
        Optional<VariableEntry> opt = values.stream()
                .filter(entry -> entry.key.equals(name.lexeme)).findFirst();
        if (opt.isPresent()) {
            return opt.get().var;
        }

        if (enclosing != null) {
            return enclosing.get(name);
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    Object getAt(int distance, String name) {
        return ancestor(distance).values.stream()
                .filter(entry -> entry.key.equals(name))
                .findFirst()
                .orElseThrow(() -> new RuntimeError(null,
                        "Undefined variable '" + name + "'."))
                .var;
    }

    Object getAt(int distance, int idx) {
        return ancestor(distance).values.get(idx).var;
    }

    void assign(Token name, Object value) {
        Optional<VariableEntry> opt = values.stream()
                .filter(entry -> entry.key.equals(name.lexeme)).findFirst();
        if (opt.isPresent()) {
            opt.get().var = value;
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    void assignAt(int distance, Token name, int idx, Object value) {
        Environment ancestor = ancestor(distance);
        ancestor.values.set(idx, new VariableEntry(name.lexeme, value));
    }

    public void define(String name, Object value) {
        values.add(new VariableEntry(name, value));
    }

    Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }

        return environment;
    }

}
