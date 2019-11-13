/*
 * Copyright 2005 JBoss Inc
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

package org.drools.modelcompiler.builder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.drools.compiler.builder.impl.KnowledgeBuilderConfigurationImpl;
import org.drools.compiler.compiler.DialectCompiletimeRegistry;
import org.drools.compiler.lang.descr.EntryPointDeclarationDescr;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.ruleunit.RuleUnitDescription;
import org.drools.model.DomainClassMetadata;
import org.drools.model.Global;
import org.drools.model.Model;
import org.drools.model.Query;
import org.drools.model.Rule;
import org.drools.model.WindowReference;
import org.drools.modelcompiler.builder.generator.DRLIdGenerator;
import org.drools.modelcompiler.builder.generator.DrlxParseUtil;
import org.drools.modelcompiler.builder.generator.QueryGenerator;
import org.drools.modelcompiler.builder.generator.QueryParameter;
import org.kie.api.runtime.rule.AccumulateFunction;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import static com.github.javaparser.StaticJavaParser.parseBodyDeclaration;
import static com.github.javaparser.ast.Modifier.finalModifier;
import static com.github.javaparser.ast.Modifier.publicModifier;
import static com.github.javaparser.ast.Modifier.staticModifier;
import static org.drools.core.impl.StatefulKnowledgeSessionImpl.DEFAULT_RULE_UNIT;
import static org.drools.modelcompiler.builder.generator.DrlxParseUtil.toClassOrInterfaceType;
import static org.drools.modelcompiler.builder.generator.DrlxParseUtil.toVar;
import static org.drools.modelcompiler.builder.generator.DslMethodNames.GLOBAL_OF_CALL;
import static org.drools.modelcompiler.builder.generator.QueryGenerator.QUERY_METHOD_PREFIX;
import static org.drools.modelcompiler.util.ClassUtil.asJavaSourceName;
import static org.drools.modelcompiler.util.ClassUtil.getAccessibleProperties;
import static org.drools.modelcompiler.util.StringUtil.md5Hash;

public class PackageModel {

    public static final String DATE_TIME_FORMATTER_FIELD = "DATE_TIME_FORMATTER";
    public static final String STRING_TO_DATE_METHOD = "string_2_date";

    private static final String RULES_FILE_NAME = "Rules";

    public static final String DOMAIN_CLASSESS_METADATA_FILE_NAME = "DomainClassesMetadata";
    public static final String DOMAIN_CLASS_METADATA_INSTANCE = "_Metadata_INSTANCE";

    private static final int RULES_DECLARATION_PER_CLASS = 1000;

    private final String name;
    private final boolean isPattern;
    private final DialectCompiletimeRegistry dialectCompiletimeRegistry;

    private final String rulesFileName;
    
    private Set<String> imports = new HashSet<>();
    private Set<String> staticImports = new HashSet<>();
    private Set<String> entryPoints = new HashSet<>();
    private Map<String, Method> staticMethods;

    private Map<String, Class<?>> globals = new HashMap<>();

    private Map<String, List<MethodDeclaration>> ruleMethods = new HashMap<>();

    private Map<String, MethodDeclaration> queryMethods = new HashMap<>();
    private Map<String, Set<QueryModel>> queriesByRuleUnit = new HashMap<>();

    private Map<String, QueryGenerator.QueryDefWithType> queryDefWithType = new HashMap<>();

    private Map<String, MethodCallExpr> windowReferences = new HashMap<>();

    private Map<String, List<QueryParameter>> queryVariables = new HashMap<>();

    private List<MethodDeclaration> functions = new ArrayList<>();

    private List<ClassOrInterfaceDeclaration> generatedPOJOs = new ArrayList<>();
    private List<GeneratedClassWithPackage> generatedAccumulateClasses = new ArrayList<>();

    private Set<Class<?>> domainClasses = new HashSet<>();

    private List<Expression> typeMetaDataExpressions = new ArrayList<>();

    private DRLIdGenerator exprIdGenerator;

    private KnowledgeBuilderConfigurationImpl configuration;
    private Map<String, AccumulateFunction> accumulateFunctions;
    private InternalKnowledgePackage pkg;

    private final String pkgUUID;
    private Set<RuleUnitDescription> ruleUnits = new HashSet<>();

    public PackageModel(String name, KnowledgeBuilderConfigurationImpl configuration, boolean isPattern, DialectCompiletimeRegistry dialectCompiletimeRegistry, DRLIdGenerator exprIdGenerator) {
        this("", name, configuration, isPattern, dialectCompiletimeRegistry, exprIdGenerator);
    }

    public PackageModel(String gav, String name, KnowledgeBuilderConfigurationImpl configuration, boolean isPattern, DialectCompiletimeRegistry dialectCompiletimeRegistry, DRLIdGenerator exprIdGenerator) {
        this.name = name;
        this.pkgUUID = md5Hash(gav+name);
        this.isPattern = isPattern;
        this.rulesFileName = RULES_FILE_NAME + pkgUUID;
        this.configuration = configuration;
        this.exprIdGenerator = exprIdGenerator;
        this.dialectCompiletimeRegistry = dialectCompiletimeRegistry;
    }

    public String getPackageUUID() {
        return pkgUUID;
    }

    public String getDomainClassName( Class<?> clazz ) {
        return DOMAIN_CLASSESS_METADATA_FILE_NAME + getPackageUUID() + "." + asJavaSourceName( clazz ) + DOMAIN_CLASS_METADATA_INSTANCE;
    }

    public String getRulesFileName() {
        return rulesFileName;
    }

    public KnowledgeBuilderConfigurationImpl getConfiguration() {
        return configuration;
    }

    public String getName() {
        return name;
    }

    public String getPathName() {
        return name.replace('.', '/');
    }
    
    public DRLIdGenerator getExprIdGenerator() {
        return exprIdGenerator;
    }

    public void addImports(Collection<String> imports) {
        this.imports.addAll(imports);
    }

    public Collection<String> getImports() {
        return this.imports;
    }

    public void addStaticImports(Collection<String> imports) {
        this.staticImports.addAll(imports);
    }

    public void addEntryPoints(Collection<EntryPointDeclarationDescr> entryPoints) {
        entryPoints.stream().map( EntryPointDeclarationDescr::getEntryPointId ).forEach( this.entryPoints::add );
    }

    public void addEntryPoint(String name) {
        entryPoints.add( name );
    }

    public boolean hasEntryPoint(String name) {
        return entryPoints.contains( name );
    }

    public Collection<String> getStaticImports() {
        return this.staticImports;
    }

    public Method getStaticMethod(String methodName) {
        return getStaticMethods().get(methodName);
    }

    private Map<String, Method> getStaticMethods() {
        if (staticMethods == null) {
            staticMethods = new HashMap<>();
            for (String i : staticImports) {
                if (i.endsWith( ".*" )) {
                    String className = i.substring( 0, i.length()-2 );
                    try {
                        Class<?> importedClass = pkg.getTypeResolver().resolveType( className );
                        for (Method m : importedClass.getMethods()) {
                            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                                staticMethods.put(m.getName(), m);
                            }
                        }
                    } catch (ClassNotFoundException e1) {
                        throw new UnsupportedOperationException("Class not found", e1);
                    }
                } else {
                    int splitPoint = i.lastIndexOf( '.' );
                    String className = i.substring( 0, splitPoint );
                    String methodName = i.substring( splitPoint+1 );
                    try {
                        Class<?> importedClass = pkg.getTypeResolver().resolveType( className );
                        for (Method m : importedClass.getMethods()) {
                            if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getName().equals( methodName )) {
                                staticMethods.put(methodName, m);
                                break;
                            }
                        }
                    } catch (ClassNotFoundException e1) {
                        throw new UnsupportedOperationException("Class not found", e1);
                    }
                }
            }
        }
        return staticMethods;
    }

    public void addGlobals(InternalKnowledgePackage pkg) {
        globals.putAll( pkg.getGlobals() );
    }

    public void addGlobal(String name, Class<?> type) {
        globals.put( name, type );
    }

    public Map<String, Class<?>> getGlobals() {
        return globals;
    }

    public void addTypeMetaDataExpressions(Expression typeMetaDataExpression) {
        typeMetaDataExpressions.add(typeMetaDataExpression);
    }

    public void putRuleMethod(String unitName, MethodDeclaration ruleMethod) {
        ruleMethods.computeIfAbsent(unitName, k -> new ArrayList<>()).add( ruleMethod );
    }

    public void putQueryMethod(MethodDeclaration queryMethod) {
        this.queryMethods.put(queryMethod.getNameAsString(), queryMethod);
    }

    public MethodDeclaration getQueryMethod(String key) {
        return queryMethods.get(key);
    }

    public void putQueryVariable(String queryName, QueryParameter qp) {
        this.queryVariables.computeIfAbsent(queryName, k -> new ArrayList<>());
        this.queryVariables.get(queryName).add(qp);
    }

    public List<QueryParameter> queryVariables(String queryName) {
        return this.queryVariables.get(queryName);
    }

    public Map<String, QueryGenerator.QueryDefWithType> getQueryDefWithType() {
        return queryDefWithType;
    }

    public void addAllFunctions(List<MethodDeclaration> functions) {
        this.functions.addAll(functions);
    }

    public void addGeneratedPOJO(ClassOrInterfaceDeclaration pojo) {
        this.generatedPOJOs.add(pojo);
    }

    public List<ClassOrInterfaceDeclaration> getGeneratedPOJOsSource() {
        return generatedPOJOs;
    }

    public void addGeneratedAccumulateClasses(GeneratedClassWithPackage clazz) {
        this.generatedAccumulateClasses.add(clazz);
    }

    public List<GeneratedClassWithPackage> getGeneratedAccumulateClasses() {
        return generatedAccumulateClasses;
    }

    public void addAllWindowReferences(String methodName, MethodCallExpr windowMethod) {
        this.windowReferences.put(methodName, windowMethod);
    }

    public Map<String, MethodCallExpr> getWindowReferences() {
        return windowReferences;
    }

    final static Type WINDOW_REFERENCE_TYPE = StaticJavaParser.parseType(WindowReference.class.getCanonicalName());

    public List<MethodDeclaration> getFunctions() {
        return functions;
    }

    public Map<String, AccumulateFunction> getAccumulateFunctions() {
        return accumulateFunctions;
    }

    public void setInternalKnowledgePackage(InternalKnowledgePackage pkg) {
        this.pkg = pkg;
    }

    public InternalKnowledgePackage getPkg() {
        return pkg;
    }


    public DialectCompiletimeRegistry getDialectCompiletimeRegistry() {
        return dialectCompiletimeRegistry;
    }

    public void addRuleUnit(RuleUnitDescription ruleUnitType) {
        this.ruleUnits.add(ruleUnitType);
    }

    public Collection<RuleUnitDescription> getRuleUnits() {
        return ruleUnits;
    }

    public void addQueryInRuleUnit(RuleUnitDescription ruleUnitType, QueryModel query) {
        addRuleUnit(ruleUnitType);
        queriesByRuleUnit.computeIfAbsent( ruleUnitType.getRuleUnitName(), k -> new HashSet<>() ).add(query);
    }

    public Collection<QueryModel> getQueriesInRuleUnit(RuleUnitDescription ruleUnitType) {
        return queriesByRuleUnit.getOrDefault( ruleUnitType.getRuleUnitName(), Collections.emptySet() );
    }

    public static class RuleSourceResult {

        private final CompilationUnit mainRuleClass;
        private Collection<CompilationUnit> modelClasses = new ArrayList<>();
        private Map<String, String> modelsByUnit = new HashMap<>();

        public RuleSourceResult(CompilationUnit mainRuleClass) {
            this.mainRuleClass = mainRuleClass;
        }

        public CompilationUnit getMainRuleClass() {
            return mainRuleClass;
        }

        /**
         * Append additional class to source results.
         * @param additionalCU 
         */
        public RuleSourceResult withClass( CompilationUnit additionalCU ) {
            modelClasses.add(additionalCU);
            return this;
        }

        public RuleSourceResult withModel( String unit, String model ) {
            modelsByUnit.put(unit, model);
            return this;
        }

        public Collection<CompilationUnit> getModelClasses() {
            return Collections.unmodifiableCollection( modelClasses );
        }

        public Map<String, String> getModelsByUnit() {
            return modelsByUnit;
        }
    }

    public RuleSourceResult getRulesSource(boolean oneClassPerRule) {
        boolean hasRuleUnit = !ruleUnits.isEmpty();
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration( name );

        manageImportForCompilationUnit(cu);

        ClassOrInterfaceDeclaration rulesClass = cu.addClass(rulesFileName);
        rulesClass.addImplementedType(Model.class);
        if (hasRuleUnit) {
            rulesClass.addModifier( Modifier.Keyword.ABSTRACT );
        }

        BodyDeclaration<?> dateFormatter = parseBodyDeclaration(
                "public final static DateTimeFormatter " + DATE_TIME_FORMATTER_FIELD + " = DateTimeFormatter.ofPattern(DateUtils.getDateFormatMask(), Locale.ENGLISH);\n");
        rulesClass.addMember(dateFormatter);

        BodyDeclaration<?> string2dateMethodMethod = parseBodyDeclaration(
                "    @Override\n" +
                "    public String getName() {\n" +
                "        return \"" + name + "\";\n" +
                "    }\n"
                );
        rulesClass.addMember(string2dateMethodMethod);

        BodyDeclaration<?> getNameMethod = parseBodyDeclaration(
                "    public static Date " + STRING_TO_DATE_METHOD + "(String s) {\n" +
                "        return GregorianCalendar.from(LocalDate.parse(s, DATE_TIME_FORMATTER).atStartOfDay(ZoneId.systemDefault())).getTime();\n" +
                "    }\n"
                );
        rulesClass.addMember(getNameMethod);

        String entryPointsBuilder = entryPoints.isEmpty() ?
                "Collections.emptyList()" :
                "Arrays.asList(D.entryPoint(\"" + entryPoints.stream().collect( joining("\"), D.entryPoint(\"") ) + "\"))";

        BodyDeclaration<?> getEntryPointsMethod = parseBodyDeclaration(
                "    @Override\n" +
                "    public List<org.drools.model.EntryPoint> getEntryPoints() {\n" +
                "        return " + entryPointsBuilder + ";\n" +
                "    }\n"
                );
        rulesClass.addMember(getEntryPointsMethod);

        BodyDeclaration<?> getGlobalsMethod = parseBodyDeclaration(
                "    @Override\n" +
                "    public List<org.drools.model.Global> getGlobals() {\n" +
                "        return globals;\n" +
                "    }\n");
        rulesClass.addMember(getGlobalsMethod);

        BodyDeclaration<?> getTypeMetaDataMethod = parseBodyDeclaration(
                "    @Override\n" +
                "    public List<org.drools.model.TypeMetaData> getTypeMetaDatas() {\n" +
                "        return typeMetaDatas;\n" +
                "    }\n");
        rulesClass.addMember(getTypeMetaDataMethod);

        // end of fixed part


        for(Map.Entry<String, MethodCallExpr> windowReference : windowReferences.entrySet()) {
            FieldDeclaration f = rulesClass.addField(WINDOW_REFERENCE_TYPE, windowReference.getKey(), publicModifier().getKeyword(), staticModifier().getKeyword(), finalModifier().getKeyword());
            f.getVariables().get(0).setInitializer(windowReference.getValue());
        }

        for ( Map.Entry<String, Class<?>> g : getGlobals().entrySet() ) {
            addGlobalField(rulesClass, getName(), g.getKey(), g.getValue());
        }

        for(Map.Entry<String, QueryGenerator.QueryDefWithType> queryDef: queryDefWithType.entrySet()) {
            FieldDeclaration field = rulesClass.addField(queryDef.getValue().getQueryType(), queryDef.getKey(), publicModifier().getKeyword(), staticModifier().getKeyword(), finalModifier().getKeyword());
            field.getVariables().get(0).setInitializer(queryDef.getValue().getMethodCallExpr());
        }

        // instance initializer block.
        // add to `rules` list the result of invoking each method for rule
        InitializerDeclaration rulesListInitializer = new InitializerDeclaration();
        BlockStmt rulesListInitializerBody = new BlockStmt();
        rulesListInitializer.setBody(rulesListInitializerBody);

        buildArtifactsDeclaration( getGlobals().keySet(), rulesClass, rulesListInitializerBody, "org.drools.model.Global", "globals", true );

        if ( !typeMetaDataExpressions.isEmpty() ) {
            BodyDeclaration<?> typeMetaDatasList = parseBodyDeclaration("List<org.drools.model.TypeMetaData> typeMetaDatas = new ArrayList<>();");
            rulesClass.addMember(typeMetaDatasList);
            for (Expression expr : typeMetaDataExpressions) {
                addInitStatement( rulesListInitializerBody, expr, "typeMetaDatas" );
            }
        } else {
            BodyDeclaration<?> typeMetaDatasList = parseBodyDeclaration("List<org.drools.model.TypeMetaData> typeMetaDatas = Collections.emptyList();");
            rulesClass.addMember(typeMetaDatasList);
        }

        functions.forEach(rulesClass::addMember);

        RuleSourceResult results = new RuleSourceResult(cu);

        if (hasRuleUnit) {
            ruleMethods.keySet().forEach( unitName -> {
                String className = rulesFileName + "_" + unitName;
                ClassOrInterfaceDeclaration unitClass = createClass( className, results);
                unitClass.addExtendedType( rulesFileName );

                InitializerDeclaration unitInitializer = new InitializerDeclaration();
                BlockStmt unitInitializerBody = new BlockStmt();
                unitInitializer.setBody(unitInitializerBody);

                generateRulesInUnit( unitName, unitInitializerBody, results, unitClass, oneClassPerRule );

                Set<QueryModel> queries = queriesByRuleUnit.get( unitName );
                Collection<String> queryNames = queries == null ? Collections.emptyList() : queries.stream()
                        .map( QueryModel::getName )
                        .map( name -> QUERY_METHOD_PREFIX + name )
                        .collect( toList() );
                Collection<MethodDeclaration> queryImpls = queryNames.stream().map( queryMethods::get ).collect( toList() );
                generateQueriesInUnit( unitClass, unitInitializerBody, queryNames, queryImpls );

                if (!unitInitializerBody.getStatements().isEmpty()) {
                    unitClass.addMember( unitInitializer );
                }
           } );

        } else {
            generateRulesInUnit( DEFAULT_RULE_UNIT, rulesListInitializerBody, results, rulesClass, oneClassPerRule );
            generateQueriesInUnit( rulesClass, rulesListInitializerBody, queryMethods.keySet(), queryMethods.values() );
        }

        if (!rulesListInitializerBody.getStatements().isEmpty()) {
            rulesClass.addMember( rulesListInitializer );
        }

        return results;
    }

    private void generateQueriesInUnit( ClassOrInterfaceDeclaration rulesClass, BlockStmt initializerBody, Collection<String> queryNames, Collection<MethodDeclaration> queryImpls ) {
        if (queryNames == null || queryNames.isEmpty()) {
            BodyDeclaration<?> getQueriesMethod = parseBodyDeclaration(
                    "    @Override\n" +
                    "    public List<org.drools.model.Query> getQueries() {\n" +
                    "        return java.util.Collections.emptyList();\n" +
                    "    }\n");
            rulesClass.addMember(getQueriesMethod);
            return;
        }

        for (String queryName : queryNames) {
            FieldDeclaration field = rulesClass.addField(Query.class, queryName, finalModifier().getKeyword());
            field.getVariables().get(0).setInitializer(new MethodCallExpr(null, queryName));
        }

        BodyDeclaration<?> getQueriesMethod = parseBodyDeclaration(
                "    @Override\n" +
                "    public List<org.drools.model.Query> getQueries() {\n" +
                "        return queries;\n" +
                "    }\n");
        rulesClass.addMember(getQueriesMethod);

        queryImpls.forEach(rulesClass::addMember);
        buildArtifactsDeclaration( queryNames, rulesClass, initializerBody, "org.drools.model.Query", "queries", false );
    }

    private void generateRulesInUnit( String ruleUnitName, BlockStmt rulesListInitializerBody, RuleSourceResult results,
                                      ClassOrInterfaceDeclaration rulesClass, boolean oneClassPerRule ) {

        results.withModel( name + "." + ruleUnitName, name + "." + rulesClass.getNameAsString() );

        List<MethodDeclaration> ruleMethodsInUnit = ruleMethods.get(ruleUnitName);
        if (ruleMethodsInUnit == null || ruleMethodsInUnit.isEmpty()) {
            BodyDeclaration<?> getQueriesMethod = parseBodyDeclaration(
                    "    @Override\n" +
                            "    public List<org.drools.model.Rule> getRules() {\n" +
                            "        return java.util.Collections.emptyList();\n" +
                            "    }\n");
            rulesClass.addMember(getQueriesMethod);
            return;
        }

        createAndAddGetRulesMethod( rulesClass );

        int ruleCount = ruleMethodsInUnit.size();
        boolean requiresMultipleRulesLists = ruleCount >= RULES_DECLARATION_PER_CLASS-1;

        MethodCallExpr rules = buildRulesField( rulesClass );
        if (requiresMultipleRulesLists) {
            addRulesList( rulesListInitializerBody, "rulesList" );
        }

        ruleMethodsInUnit.parallelStream().forEach( DrlxParseUtil::transformDrlNameExprToNameExpr);

        int maxLength = ruleMethodsInUnit
                .parallelStream()
                .map( MethodDeclaration::toString ).mapToInt( String::length ).max().orElse( 1 );
        int rulesPerClass = oneClassPerRule ? 1 : Math.max( 50000 / maxLength, 1 );

        // each method per Drlx parser result
        int count = -1;
        Map<Integer, ClassOrInterfaceDeclaration> splitted = new LinkedHashMap<>();
        for (MethodDeclaration ruleMethod : ruleMethodsInUnit) {
            String methodName = ruleMethod.getNameAsString();
            ClassOrInterfaceDeclaration rulesMethodClass = splitted.computeIfAbsent(++count / rulesPerClass, i -> {
                String className = rulesClass.getNameAsString() + (oneClassPerRule ? "_" + methodName : "RuleMethods" + i);
                return createClass( className, results );
            });
            rulesMethodClass.addMember(ruleMethod);

            if (count % RULES_DECLARATION_PER_CLASS == RULES_DECLARATION_PER_CLASS-1) {
                int index = count / RULES_DECLARATION_PER_CLASS;
                rules = buildRulesField(results, index);
                addRulesList( rulesListInitializerBody, rulesFileName + "Rules" + index + ".rulesList" );
            }

            // manage in main class init block:
            rules.addArgument(new MethodCallExpr(new NameExpr(rulesMethodClass.getNameAsString()), methodName));
        }

        BodyDeclaration<?> rulesList = requiresMultipleRulesLists ?
                parseBodyDeclaration("List<org.drools.model.Rule> rules = new ArrayList<>(" + ruleCount + ");") :
                parseBodyDeclaration("List<org.drools.model.Rule> rules = rulesList;");
        rulesClass.addMember(rulesList);
    }

    private void createAndAddGetRulesMethod( ClassOrInterfaceDeclaration rulesClass ) {
        BodyDeclaration<?> getRulesMethod = parseBodyDeclaration(
                "    @Override\n" +
                        "    public List<org.drools.model.Rule> getRules() {\n" +
                        "        return rules;\n" +
                        "    }\n"
        );
        rulesClass.addMember( getRulesMethod );

        StringBuilder sb = new StringBuilder("\n");
        sb.append("With the following expression ID:\n");
        sb.append(exprIdGenerator.toString());
        sb.append("\n");
        JavadocComment exprIdComment = new JavadocComment(sb.toString());
        getRulesMethod.setComment(exprIdComment);
    }

    private ClassOrInterfaceDeclaration createClass( String className, RuleSourceResult results ) {
        CompilationUnit cuRulesMethod = new CompilationUnit();
        results.withClass(cuRulesMethod);
        cuRulesMethod.setPackageDeclaration(name);
        manageImportForCompilationUnit(cuRulesMethod);
        cuRulesMethod.addImport(name + "." + rulesFileName, true, true);
        return cuRulesMethod.addClass(className);
    }

    private void buildArtifactsDeclaration( Collection<String> artifacts, ClassOrInterfaceDeclaration rulesClass,
                                            BlockStmt rulesListInitializerBody, String type, String fieldName, boolean needsToVar ) {
        if (!artifacts.isEmpty()) {
            BodyDeclaration<?> queriesList = parseBodyDeclaration("List<" + type + "> " + fieldName + " = new ArrayList<>();");
            rulesClass.addMember(queriesList);
            for (String name : artifacts) {
                addInitStatement( rulesListInitializerBody, new NameExpr( needsToVar ? toVar(name) : name ), fieldName );
            }
        } else {
            BodyDeclaration<?> queriesList = parseBodyDeclaration("List<" + type + "> " + fieldName + " = Collections.emptyList();");
            rulesClass.addMember(queriesList);
        }
    }

    private void addInitStatement( BlockStmt rulesListInitializerBody, Expression expr, String fieldName ) {
        NameExpr rulesFieldName = new NameExpr( fieldName );
        MethodCallExpr add = new MethodCallExpr( rulesFieldName, "add" );
        add.addArgument( expr );
        rulesListInitializerBody.addStatement( add );
    }

    private void addRulesList( BlockStmt rulesListInitializerBody, String listName ) {
        MethodCallExpr add = new MethodCallExpr(new NameExpr("rules"), "addAll");
        add.addArgument(listName);
        rulesListInitializerBody.addStatement(add);
    }

    private MethodCallExpr buildRulesField(RuleSourceResult results, int index) {
        CompilationUnit cu = new CompilationUnit();
        results.withClass(cu);
        cu.setPackageDeclaration(name);
        cu.addImport(Arrays.class.getCanonicalName());
        cu.addImport(List.class.getCanonicalName());
        cu.addImport(Rule.class.getCanonicalName());
        String currentRulesMethodClassName = rulesFileName + "Rules" + index;
        ClassOrInterfaceDeclaration rulesClass = cu.addClass(currentRulesMethodClassName);
        return buildRulesField( rulesClass );
    }

    private MethodCallExpr buildRulesField( ClassOrInterfaceDeclaration rulesClass ) {
        MethodCallExpr rulesInit = new MethodCallExpr( null, "Arrays.asList" );
        ClassOrInterfaceType rulesType = new ClassOrInterfaceType(null, new SimpleName("List"), new NodeList<Type>(new ClassOrInterfaceType(null, "Rule")));
        VariableDeclarator rulesVar = new VariableDeclarator( rulesType, "rulesList", rulesInit );
        rulesClass.addMember( new FieldDeclaration( NodeList.nodeList( publicModifier(), staticModifier()), rulesVar ) );
        return rulesInit;
    }

    private void manageImportForCompilationUnit(CompilationUnit cu) {
        // fixed part
        cu.addImport("java.util.*");
        cu.addImport("org.drools.model.*");
        if(isPattern) {
            cu.addImport("org.drools.modelcompiler.dsl.pattern.D");
        } else {
            cu.addImport("org.drools.modelcompiler.dsl.flow.D");
        }
        cu.addImport("org.drools.model.Index.ConstraintType");
        cu.addImport("java.time.*");
        cu.addImport("java.time.format.*");
        cu.addImport("java.text.*");
        cu.addImport("org.drools.core.util.*");

        // imports from DRL:
        for ( String i : imports ) {
            if ( i.equals(name+".*") ) {
                continue; // skip same-package star import.
            }
            cu.addImport(i);
        }
        for (String i : staticImports) {
            cu.addImport( i, true, false );
        }
    }

    private static void addGlobalField(ClassOrInterfaceDeclaration classDeclaration, String packageName, String globalName, Class<?> globalClass) {
        ClassOrInterfaceType varType = toClassOrInterfaceType(Global.class);
        varType.setTypeArguments(DrlxParseUtil.classToReferenceType(globalClass));
        Type declType = DrlxParseUtil.classToReferenceType(globalClass);

        MethodCallExpr declarationOfCall = new MethodCallExpr(null, GLOBAL_OF_CALL);
        declarationOfCall.addArgument(new ClassExpr(declType ));
        declarationOfCall.addArgument(new StringLiteralExpr(packageName));
        declarationOfCall.addArgument(new StringLiteralExpr(globalName));

        FieldDeclaration field = classDeclaration.addField(varType, toVar(globalName), publicModifier().getKeyword(), staticModifier().getKeyword(), finalModifier().getKeyword());

        field.getVariables().get(0).setInitializer(declarationOfCall);
    }

    public void addAccumulateFunctions(Map<String, AccumulateFunction> accumulateFunctions) {
        this.accumulateFunctions = accumulateFunctions;
    }

    public boolean hasDeclaration(String id) {
        return globals.get(id) != null;
    }

    public boolean registerDomainClass(Class<?> domainClass) {
        if (!domainClass.isPrimitive() && !domainClass.isArray()) {
            domainClasses.add( domainClass );
            return true;
        }
        return false;
    }

    public String getDomainClassesMetadataSource() {
        StringBuilder sb = new StringBuilder(
                "package " + name + ";\n" +
                "public class " + DOMAIN_CLASSESS_METADATA_FILE_NAME  + pkgUUID + " {\n\n"
        );
        for (Class<?> domainClass : domainClasses) {
            String domainClassSourceName = asJavaSourceName( domainClass );
            List<String> accessibleProperties = getAccessibleProperties( domainClass );
            sb.append( "    public static final " + DomainClassMetadata.class.getCanonicalName() + " " + domainClassSourceName + DOMAIN_CLASS_METADATA_INSTANCE + " = new " + domainClassSourceName+ "_Metadata();\n" );
            sb.append( "    private static class " + domainClassSourceName + "_Metadata implements " + DomainClassMetadata.class.getCanonicalName() + " {\n\n" );
            sb.append(
                    "        @Override\n" +
                    "        public Class<?> getDomainClass() {\n" +
                    "            return " + domainClass.getCanonicalName() + ".class;\n" +
                    "        }\n" +
                    "\n" +
                    "        @Override\n" +
                    "        public int getPropertiesSize() {\n" +
                    "            return " + accessibleProperties.size() + ";\n" +
                    "        }\n\n" +
                    "        @Override\n" +
                    "        public int getPropertyIndex( String name ) {\n" +
                    "            switch(name) {\n"
            );
            for (int i = 0; i < accessibleProperties.size(); i++) {
                sb.append( "                case \"" + accessibleProperties.get(i) + "\": return " + i + ";\n" );
            }
            sb.append(
                    "             }\n" +
                    "             throw new RuntimeException(\"Unknown property '\" + name + \"' for class class " + domainClass + "\");\n" +
                    "        }\n" +
                    "    }\n\n"
            );
        }
        sb.append( "}" );

        return sb.toString();
    }
}
