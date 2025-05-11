package com.gmail.coldrain608.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, VariableState>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private static class VariableState {
        VariableStage stage;
        Token declare;
        Token define;
        Token use;
        int idx;
    }

    private enum VariableStage {
        DECLARED,
        DEFINED,
        USED
    }

    private enum FunctionType {
        NONE,
        METHOD,
        FUNCTION,
        INITIALIZER
    }

    private enum ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    private ClassType currentClass = ClassType.NONE;

    public void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    private void endScope() {
        Map<String, VariableState> pop = scopes.pop();
//        pop.forEach((var, state) -> {
//            if (state.stage != VariableStage.USED) {
//                Lox.error(state.declare, "variable " + var + " is not used.");
//            }
//        });
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) return;

        VariableState prevState = scopes.peek().get(name.lexeme);

        assert prevState == null : "variable has been declared before.";

        Map<String, VariableState> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name,
                    "Already variable with this name in this scope.");
        }
        VariableState variableState = new VariableState();
        variableState.stage = VariableStage.DECLARED;
        variableState.declare = name;
        variableState.idx = scope.size();
        scope.put(name.lexeme, variableState);
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        VariableState prevState = scopes.peek().get(name.lexeme);

        assert prevState != null
                && prevState.stage == VariableStage.DECLARED : "variable define before declared.";

        VariableState variableState = new VariableState();
        variableState.stage = VariableStage.DEFINED;
        variableState.declare = prevState.declare;
        variableState.define = name;
        variableState.idx = prevState.idx;

        scopes.peek().put(name.lexeme, variableState);
    }

    private void use(Token name) {
        if (scopes.isEmpty()) return;
        List<VariableState> states = scopes.stream().filter(map -> map.containsKey(name.lexeme))
                .map((map) -> map.get(name.lexeme))
                .collect(Collectors.toList());
        VariableState prevState = states.get(states.size() - 1);

        assert prevState != null
                && prevState.stage == VariableStage.DECLARED : "variable define before declared.";

        VariableState variableState = new VariableState();
        variableState.stage = VariableStage.USED;
        variableState.declare = prevState.declare;
        variableState.define = name;
        variableState.use = name;
        variableState.idx = prevState.idx;

        scopes.peek().put(name.lexeme, variableState);
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i, scopes.get(i).get(name.lexeme).idx);
                return;
            }
        }
    }

    private void resolveFunction(
            Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        beginScope();
        for (Token param : function.params) {
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;
        declare(stmt.name);
        define(stmt.name);
        if (stmt.superclass != null &&
                stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
            Lox.error(stmt.superclass.name,
                    "A class can't inherit from itself.");
        }
        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superclass);
        }
        if (stmt.superclass != null) {
            beginScope();
            VariableState variableState = new VariableState();
            variableState.declare = stmt.superclass.name;
            variableState.define = stmt.superclass.name;
            variableState.use = stmt.superclass.name;
            variableState.stage = VariableStage.USED;
            variableState.idx = scopes.size();
            scopes.peek().put("super", variableState);
        }
        beginScope();
        VariableState thisState = new VariableState();
        thisState.stage = VariableStage.USED;
        thisState.declare = stmt.name;
        thisState.define = stmt.name;
        thisState.use = stmt.name;
        thisState.idx = scopes.size();
        scopes.peek().put("this", thisState);
        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER;
            }
            resolveFunction(method, declaration);
        }
        for (Stmt.Function method : stmt.klassMethods) {
            FunctionType declaration = FunctionType.METHOD;
            resolveFunction(method, declaration);
        }
        endScope();
        if (stmt.superclass != null) endScope();
        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() &&
                (scopes.peek().get(expr.name.lexeme) != null
                && scopes.peek().get(expr.name.lexeme).stage == VariableStage.DECLARED)) {
            Lox.error(expr.name,
                    "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name);

//        use(expr.name);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }
        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword,
                        "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }

        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCommaExpr(Expr.Comma expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitTernaryExpr(Expr.Ternary expr) {
        resolve(expr.cond);
        // 不 resolve then 和 else
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                    "Can't use 'super' outside of a class.");
        } else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword,
                    "Can't use 'super' in a class with no superclass.");
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                    "Can't use 'this' outside of a class.");
            return null;
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }
}
