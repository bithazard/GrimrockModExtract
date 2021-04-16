package bithazard.game.grimrock.utils;

import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.ast.Exp;
import org.luaj.vm2.ast.TableConstructor;
import org.luaj.vm2.ast.TableField;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LuaUtils {
    private static final Pattern START_OF_LINE = Pattern.compile("(?m)^");

    private LuaUtils() {
    }

    public static String escapeForLua(String rawString) throws ScriptException {
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("luaj");
        scriptEngine.put("rawString", rawString);
        scriptEngine.eval("escapedString = string.format('%q', rawString)");
        return (String)scriptEngine.get("escapedString");
    }

    public static Exp unwrapParensExp(Exp exp) {
        Exp unwrappedExp = exp;
        while (unwrappedExp instanceof Exp.ParensExp) {
            unwrappedExp = ((Exp.ParensExp)unwrappedExp).exp;
        }
        return unwrappedExp;
    }

    public static String argExpsToString(Collection<Exp> exps) {
        return exps.stream().map(LuaUtils::expToString).collect(Collectors.joining(", "));
    }

    public static String expToString(Exp exp) {
        if (exp instanceof Exp.AnonFuncDef) {
            return "Not Yet Implemented " + exp;
        }
        if (exp instanceof Exp.BinopExp) {
            Exp.BinopExp binopExp = (Exp.BinopExp)exp;
            return expToString(binopExp.lhs) + " " + opToString(binopExp.op) + " " + expToString(binopExp.rhs);
        }
        if (exp instanceof Exp.Constant) {
            Exp.Constant constant = (Exp.Constant)exp;
            if (constant.value instanceof LuaString) {
                return "\"" + constant.value.toString() + "\"";
            }
            return constant.value.toString();
        }
        if (exp instanceof Exp.FieldExp) {
            Exp.FieldExp fieldExp = (Exp.FieldExp)exp;
            String lhs = expToString(fieldExp.lhs);
            return lhs + "." + fieldExp.name.name;
        }
        if (exp instanceof Exp.MethodCall) {
            Exp.MethodCall methodCall = (Exp.MethodCall)exp;
            String lhs = expToString(methodCall.lhs);
            @SuppressWarnings("unchecked")
            List<Exp> exps = methodCall.args.exps != null ? methodCall.args.exps : Collections.emptyList();
            String methodArgsString = exps.stream().map(LuaUtils::expToString).collect(Collectors.joining(", "));
            return lhs + ":" + methodCall.name + "(" + methodArgsString + ")";
        }
        if (exp instanceof Exp.FuncCall) {
            Exp.FuncCall funcCall = (Exp.FuncCall)exp;
            String lhs = expToString(funcCall.lhs);
            @SuppressWarnings("unchecked")
            List<Exp> exps = funcCall.args.exps != null ? funcCall.args.exps : Collections.emptyList();
            String functionArgsString = exps.stream().map(LuaUtils::expToString).collect(Collectors.joining(", "));
            return lhs + "(" + functionArgsString + ")";
        }
        if (exp instanceof Exp.IndexExp) {
            Exp.IndexExp indexExp = (Exp.IndexExp)exp;
            return expToString(indexExp.lhs) + "[" + expToString(indexExp.exp) + "]";
        }
        if (exp instanceof Exp.NameExp) {
            Exp.NameExp nameExp = (Exp.NameExp)exp;
            return nameExp.name.name;
        }
        if (exp instanceof Exp.ParensExp) {
            Exp.ParensExp parensExp = (Exp.ParensExp)exp;
            return "(" + expToString(parensExp.exp) + ")";
        }
        if (exp instanceof Exp.UnopExp) {
            return "Not Yet Implemented " + exp;
        }
        if (exp instanceof Exp.VarargsExp) {
            return "Not Yet Implemented " + exp;
        }
        if (exp instanceof TableConstructor) {
            TableConstructor tableConstructor = (TableConstructor)exp;
            @SuppressWarnings("unchecked")
            List<TableField> fields = tableConstructor.fields;
            List<String> tableFields = new ArrayList<>();
            for (TableField field : fields) {
                tableFields.add(field.name + " = " + expToString(field.rhs));
            }
            String tableContent = START_OF_LINE.matcher(String.join(",\n", tableFields)).replaceAll("    ");
            return "{\n" + tableContent + "\n}";
        }
        throw new AssertionError("Passed exp has unexpected type");
    }

    private static String opToString(int op) {
        switch (op) {
            case Lua.OP_OR:
                return "or";
            case Lua.OP_AND:
                return "and";
            case Lua.OP_LT:
                return "<";
            case Lua.OP_GT:
                return ">";
            case Lua.OP_LE:
                return "<=";
            case Lua.OP_GE:
                return ">=";
            case Lua.OP_NEQ:
                return "~=";
            case Lua.OP_EQ:
                return "==";
            case Lua.OP_CONCAT:
                return "..";
            case Lua.OP_ADD:
                return "+";
            case Lua.OP_SUB:
                return "-";
            case Lua.OP_MUL:
                return "*";
            case Lua.OP_DIV:
                return "/";
            case Lua.OP_MOD:
                return "%";
            case Lua.OP_NOT:
                return "not";
            case Lua.OP_UNM:
                return "-";
            case Lua.OP_LEN:
                return "#";
            case Lua.OP_POW:
                return "^";
            default:
                throw new IllegalArgumentException("Unknown op: " + op);
        }
    }
}
