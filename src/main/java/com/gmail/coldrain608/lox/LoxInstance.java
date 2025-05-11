package com.gmail.coldrain608.lox;

import java.util.HashMap;
import java.util.Map;

class LoxInstance {
    private final LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    // kclass 为 null 时为 metaClass 的 instance
    LoxInstance(LoxClass klass) {
        this.klass = klass;
    }

    Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }
        if (klass != null) {
            LoxFunction method = klass.findMethod(name.lexeme);
            if (method != null) return method.bind(this);
        }

        throw new RuntimeError(name,
                "Undefined property '" + name.lexeme + "'.");
    }

    void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }

    public LoxClass getKlass() {
        return klass;
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}

