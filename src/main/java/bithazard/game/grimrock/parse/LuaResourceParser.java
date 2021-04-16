package bithazard.game.grimrock.parse;

import bithazard.game.grimrock.utils.LuaUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.luaj.vm2.ast.Chunk;
import org.luaj.vm2.ast.Exp;
import org.luaj.vm2.ast.TableConstructor;
import org.luaj.vm2.ast.TableField;
import org.luaj.vm2.ast.Visitor;
import org.luaj.vm2.parser.LuaParser;
import org.luaj.vm2.parser.ParseException;
import org.luaj.vm2.parser.TokenMgrError;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LuaResourceParser {
    private static final byte[] NEWLINE_BYTES = "\n".getBytes(StandardCharsets.UTF_8);
    private static final Pattern FBX_FILE_ENDING = Pattern.compile("\\.fbx$");
    private static final Pattern TGA_FILE_ENDING = Pattern.compile("\\.tga$");
    private static final Pattern SEX_PLACEHOLDER = Pattern.compile("\\$sex");
    private static final Map<String, BiFunction<Exp, ErrorCollector, Collection<String>>> TABLE_FIELD_PARSERS = new HashMap<>();
    private static final Map<String, BiFunction<List<Exp>, ErrorCollector, Collection<String>>> METHOD_PARSERS = new HashMap<>();
    private static final Map<String, BiFunction<List<Exp>, ErrorCollector, Collection<String>>> FUNCTION_PARSERS = new HashMap<>();

    private static final BiFunction<Exp, ErrorCollector, Collection<String>> SINGLE_VALUE_TABLE_FIELD_PARSER = (exp, errorCollector) -> {
        Exp unwrappedExp = LuaUtils.unwrapParensExp(exp);
        if (unwrappedExp instanceof Exp.Constant) {
            return Set.of(((Exp.Constant)unwrappedExp).value.toString());
        }
        errorCollector.addError("Error parsing table field. Expected constant expression (string).", exp);
        return Collections.emptySet();
    };

    private static final BiFunction<Exp, ErrorCollector, Collection<String>> MULTI_VALUE_TABLE_FIELD_PARSER = (exp, errorCollector) -> {
        if (exp instanceof TableConstructor) {
            @SuppressWarnings("unchecked")
            List<TableField> tableFields = ((TableConstructor)exp).fields;
            return parseTableFields(tableFields);
        }
        errorCollector.addError("Error parsing table field. Expected table constructor.", exp);
        return Collections.emptySet();
    };

    private static final BiFunction<Exp, ErrorCollector, Collection<String>> SINGLE_OR_MULTI_VALUE_TABLE_FIELD_PARSER = (exp, errorCollector) -> {
        Exp unwrappedExp = LuaUtils.unwrapParensExp(exp);
        if (unwrappedExp instanceof Exp.Constant) {
            return Set.of(((Exp.Constant)unwrappedExp).value.toString());
        }
        if (exp instanceof TableConstructor) {
            @SuppressWarnings("unchecked")
            List<TableField> tableFields = ((TableConstructor)exp).fields;
            return parseTableFields(tableFields);
        }
        errorCollector.addError("Error parsing table field. Expected constant expression or table constructor.", exp);
        return Collections.emptySet();
    };

    private static final BiFunction<Exp, ErrorCollector, Collection<String>> CONTAINED_SCRIPT_TABLE_FIELD_PARSER = (exp, errorCollector) -> {
        if (exp instanceof Exp.Constant) {
            return parseSubScript((Exp.Constant)exp, errorCollector);
        }
        errorCollector.addError("Error parsing table field. Expected constant expression (script).", exp);
        return Collections.emptySet();
    };

    private static final BiFunction<List<Exp>, ErrorCollector, Collection<String>> FIRST_ARGUMENT_METHOD_PARSER = (exps, errorCollector) -> {
        Exp methodArg1 = exps.get(0);
        if (methodArg1 instanceof Exp.Constant) {
            return Set.of(((Exp.Constant)methodArg1).value.toString());
        }
        errorCollector.addError("Error parsing argument. Expected constant expression (string)", methodArg1);
        return Collections.emptySet();
    };

    private static final BiFunction<List<Exp>, ErrorCollector, Collection<String>> PASSED_SCRIPT_METHOD_PARSER = (exps, errorCollector) -> {
        Exp scriptExp = exps.get(0);
        if (scriptExp instanceof Exp.Constant) {
            return parseSubScript((Exp.Constant)scriptExp, errorCollector);
        }
        errorCollector.addError("Error parsing argument. Expected constant expression (script)", scriptExp);
        return Collections.emptySet();
    };

    private static final BiFunction<List<Exp>, ErrorCollector, Collection<String>> FIRST_ARGUMENT_FUNCTION_PARSER = FIRST_ARGUMENT_METHOD_PARSER;

    private static final Function<Collection<String>, Collection<String>> CHANGE_FILE_ENDING_TO_ANIMATION =
            strings -> strings.stream().map(s -> FBX_FILE_ENDING.matcher(s).replaceAll(".animation")).collect(Collectors.toSet());

    private static final Function<Collection<String>, Collection<String>> CHANGE_FILE_ENDING_TO_MODEL =
            strings -> strings.stream().map(s -> FBX_FILE_ENDING.matcher(s).replaceAll(".model")).collect(Collectors.toSet());

    private static final Function<Collection<String>, Collection<String>> CHANGE_FILE_ENDING_TO_DDS =
            strings -> strings.stream().map(s -> TGA_FILE_ENDING.matcher(s).replaceAll(".dds")).collect(Collectors.toSet());

    private static final Function<Collection<String>, Collection<String>> RESOLVE_SEX_PLACEHOLDER = strings -> {
        Set<String> result = new LinkedHashSet<>();
        for (String string : strings) {
            Matcher matcher = SEX_PLACEHOLDER.matcher(string);
            if (matcher.find()) {
                result.add(matcher.replaceAll("male"));
                result.add(matcher.replaceAll("female"));
            } else {
                result.add(string);
            }
        }
        return result;
    };

    static {
        TABLE_FIELD_PARSERS.put("animation", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_ANIMATION));
        TABLE_FIELD_PARSERS.put("animations", MULTI_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_ANIMATION));
        TABLE_FIELD_PARSERS.put("model", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_MODEL));
        TABLE_FIELD_PARSERS.put("emitterMesh", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_MODEL));
        TABLE_FIELD_PARSERS.put("gfxAtlas", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("diffuseMap", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("specularMap", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("normalMap", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("emissiveMap", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("displacementMap", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("iconAtlas", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("texture", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("spellIconAtlas", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("image", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("portrait", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("shadeTex", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("inventoryBackground", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(RESOLVE_SEX_PLACEHOLDER).andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("clouds0Map", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("clouds1Map", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("clouds2Map", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("clouds3Map", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("cloudsRim1Map", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("cloudsRim2Map", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("cloudsRim3Map", SINGLE_VALUE_TABLE_FIELD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        TABLE_FIELD_PARSERS.put("ScrollImage", SINGLE_VALUE_TABLE_FIELD_PARSER);
        TABLE_FIELD_PARSERS.put("filename", SINGLE_OR_MULTI_VALUE_TABLE_FIELD_PARSER);
        TABLE_FIELD_PARSERS.put("source", CONTAINED_SCRIPT_TABLE_FIELD_PARSER);

        METHOD_PARSERS.put("setModel", FIRST_ARGUMENT_METHOD_PARSER.andThen(CHANGE_FILE_ENDING_TO_MODEL));
        METHOD_PARSERS.put("setGfxAtlas", FIRST_ARGUMENT_METHOD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        METHOD_PARSERS.put("setTexture", FIRST_ARGUMENT_METHOD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        METHOD_PARSERS.put("setImage", FIRST_ARGUMENT_METHOD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        METHOD_PARSERS.put("setPortrait", FIRST_ARGUMENT_METHOD_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        METHOD_PARSERS.put("setEmitterMesh", FIRST_ARGUMENT_METHOD_PARSER.andThen(CHANGE_FILE_ENDING_TO_MODEL));
        METHOD_PARSERS.put("playScreenEffect", FIRST_ARGUMENT_METHOD_PARSER);
        METHOD_PARSERS.put("loadFile", FIRST_ARGUMENT_METHOD_PARSER);
        METHOD_PARSERS.put("setSource", PASSED_SCRIPT_METHOD_PARSER);

        FUNCTION_PARSERS.put("drawImage", FIRST_ARGUMENT_FUNCTION_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        FUNCTION_PARSERS.put("showImage", FIRST_ARGUMENT_FUNCTION_PARSER.andThen(CHANGE_FILE_ENDING_TO_DDS));
        FUNCTION_PARSERS.put("import", FIRST_ARGUMENT_FUNCTION_PARSER);
        FUNCTION_PARSERS.put("completeGame", FIRST_ARGUMENT_FUNCTION_PARSER);
        FUNCTION_PARSERS.put("playVideo", FIRST_ARGUMENT_FUNCTION_PARSER);
    }

    public Collection<String> findResourceStrings(InputStream inputStream, ErrorCollector errorCollector) {
        Collection<String> result = new LinkedHashSet<>();
        //LuaParser throws a TokenMgrError if an input ends with a comment (--), so we simply add a newline to the end
        try (BOMInputStream bomInputStream = new BOMInputStream(inputStream, false);
            ByteArrayInputStream newLineInputStream = new ByteArrayInputStream(NEWLINE_BYTES)) {
            LuaParser parser = new LuaParser(new SequenceInputStream(bomInputStream, newLineInputStream), "UTF-8");
            Chunk chunk = parser.Chunk();

            chunk.accept(new Visitor() {
                @Override
                public void visit(TableField field) {
                    super.visit(field);
                    if (field.name == null) {
                        return;
                    }
                    BiFunction<Exp, ErrorCollector, Collection<String>> tableFieldParser = TABLE_FIELD_PARSERS.get(field.name);
                    if (tableFieldParser != null) {
                        Collection<String> parseResult = tableFieldParser.apply(field.rhs, errorCollector);
                        result.addAll(parseResult);
                    }
                }

                @Override
                public void visit(Exp.MethodCall methodCall) {
                    super.visit(methodCall);
                    @SuppressWarnings("unchecked")
                    List<Exp> methodArgs = methodCall.args.exps;
                    if (methodArgs == null) {
                        return;
                    }
                    BiFunction<List<Exp>, ErrorCollector, Collection<String>> methodParser = METHOD_PARSERS.get(methodCall.name);
                    if (methodParser != null) {
                        Collection<String> parseResult = methodParser.apply(methodArgs, errorCollector);
                        result.addAll(parseResult);
                    }
                }

                @Override
                public void visit(Exp.FuncCall funcCall) {
                    super.visit(funcCall);
                    @SuppressWarnings("unchecked")
                    List<Exp> functionArgs = funcCall.args.exps;
                    if (functionArgs == null) {
                        return;
                    }
                    String functionName;
                    try {
                        functionName = findFunctionName(funcCall);
                    } catch (FuncCallNameException e) {
                        errorCollector.addError(e.getMessage(), funcCall);
                        return;
                    }
                    BiFunction<List<Exp>, ErrorCollector, Collection<String>> functionParser = FUNCTION_PARSERS.get(functionName);
                    if (functionParser != null) {
                        Collection<String> parseResult = functionParser.apply(functionArgs, errorCollector);
                        result.addAll(parseResult);
                    }
                }
            });
        } catch (ParseException e) {
            errorCollector.addError("Parse failed: " + e.getMessage(), e.currentToken);
        } catch (TokenMgrError e) {
            errorCollector.addError("Parsing error: " + e.getMessage());
        } catch (IOException e) {
            errorCollector.addError("Read error: " + e.getMessage());
        }
        return result;
    }

    private String findFunctionName(Exp.FuncCall exp) {
        if (exp.lhs instanceof Exp.NameExp) {
            return ((Exp.NameExp)exp.lhs).name.name;
        }
        if (exp.lhs instanceof Exp.FieldExp) {
            Exp.FieldExp fieldExp = (Exp.FieldExp)exp.lhs;
            return fieldExp.name.name;
        }
        if (exp.lhs instanceof Exp.IndexExp) {
            Exp.IndexExp indexExp = (Exp.IndexExp)exp.lhs;
            throw new FuncCallNameException("Unable to determine name of dynamic function call: " + LuaUtils.expToString(indexExp));
        }
        throw new FuncCallNameException("Could not determine function name " + exp);
    }

    private static Collection<String> parseSubScript(Exp.Constant exp, ErrorCollector errorCollector) {
        String fieldValue = exp.value.toString();
        ByteArrayInputStream fieldValueInputStream = new ByteArrayInputStream(fieldValue.getBytes(StandardCharsets.UTF_8));
        ErrorCollector subErrorCollector = errorCollector.createSubErrorCollector(exp.beginLine, exp.beginColumn);
        Collection<String> resourceStrings = new LuaResourceParser().findResourceStrings(fieldValueInputStream, subErrorCollector);
        errorCollector.addAllErrors(subErrorCollector);
        return resourceStrings;
    }

    private static Collection<String> parseTableFields(List<TableField> tableFields) {
        Set<String> result = new LinkedHashSet<>();
        for (TableField tableField : tableFields) {
            if (tableField.rhs instanceof Exp.Constant) {
                result.add(((Exp.Constant)tableField.rhs).value.toString());
            }
        }
        return result;
    }
}
