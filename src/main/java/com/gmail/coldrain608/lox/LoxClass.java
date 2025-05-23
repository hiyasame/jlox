package com.gmail.coldrain608.lox;

import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
    final String name;
    final LoxClass superclass;
    // 替换部分开始
    private final Map<String, LoxFunction> methods;
    private final Map<String, LoxFunction> klassMethods;

    LoxClass(String name, LoxClass superclass, Map<String, LoxFunction> methods, Map<String, LoxFunction> klassMethods) {
        super(null);
        this.superclass = superclass;
        this.name = name;
        this.methods = methods;
        this.klassMethods = klassMethods;
    }

    @Override
    Object get(Token name) {
        // 优先从 property 里面找
        // 如果 property 里面没有才从 klassMethods 列表里找
        // 也就是说可以覆盖
        try {
            return super.get(name);
        } catch (RuntimeError error) {
            if (this.klassMethods.containsKey(name.lexeme)) {
                return this.klassMethods.get(name.lexeme).bind(this);
            }
        }
        throw new RuntimeError(name,
                "Undefined property '" + name.lexeme + "'.");
    }

    LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }
        if (superclass != null) {
            return superclass.findMethod(name);
        }

        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }
        return instance;
    }
}