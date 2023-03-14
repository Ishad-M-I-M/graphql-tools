package io.ballerina.graphql.generator.ballerina;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLUnionType;
import io.ballerina.compiler.syntax.tree.ArrayDimensionNode;
import io.ballerina.compiler.syntax.tree.ArrayTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.ClassDefinitionNode;
import io.ballerina.compiler.syntax.tree.DistinctTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.EnumMemberNode;
import io.ballerina.compiler.syntax.tree.FunctionBodyBlockNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.MethodDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.ObjectTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.RecordTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypeReferenceNode;
import io.ballerina.compiler.syntax.tree.UnionTypeDescriptorNode;
import io.ballerina.graphql.exception.ServiceTypesGenerationException;
import io.ballerina.graphql.generator.CodeGeneratorConstants;
import io.ballerina.graphql.generator.CodeGeneratorUtils;
import io.ballerina.graphql.generator.graphql.Constants;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;
import org.ballerinalang.formatter.core.Formatter;
import org.ballerinalang.formatter.core.FormatterException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createEmptyNodeList;
import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createIdentifierToken;
import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createNodeList;
import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createSeparatedNodeList;
import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createToken;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createArrayDimensionNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createArrayTypeDescriptorNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createBuiltinSimpleNameReferenceNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createClassDefinitionNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createDistinctTypeDescriptorNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createEnumDeclarationNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createEnumMemberNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createFunctionBodyBlockNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createFunctionDefinitionNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createFunctionSignatureNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createMethodDeclarationNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createModulePartNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createObjectTypeDescriptorNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createOptionalTypeDescriptorNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createRecordFieldNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createRecordTypeDescriptorNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createRequiredParameterNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createReturnTypeDescriptorNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createSimpleNameReferenceNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createStreamTypeDescriptorNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createStreamTypeParamsNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createTypeDefinitionNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createTypeReferenceNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createUnionTypeDescriptorNode;

/**
 * Generates the Ballerina syntax tree for the service types.
 */
public class ServiceTypesGenerator extends TypesGenerator {

    private String fileName;
    private boolean recordForced;
    private HashMap<String, List<GraphQLNamedType>> schemaNamedTypes;

    public ServiceTypesGenerator() {
        this.schemaNamedTypes = initializeSchemaNamedTypes();
    }

    private HashMap<String, List<GraphQLNamedType>> initializeSchemaNamedTypes() {
        HashMap<String, List<GraphQLNamedType>> schemaNamedTypes = new HashMap<>();
        schemaNamedTypes.put(Constants.GRAPHQL_OBJECT_TYPE, new ArrayList<>());
        schemaNamedTypes.put(Constants.GRAPHQL_INTERFACE_TYPE, new ArrayList<>());
        schemaNamedTypes.put(Constants.GRAPHQL_INPUT_OBJECT_TYPE, new ArrayList<>());
        schemaNamedTypes.put(Constants.GRAPHQL_ENUM_TYPE, new ArrayList<>());
        schemaNamedTypes.put(Constants.GRAPHQL_UNION_TYPE, new ArrayList<>());

        return schemaNamedTypes;
    }

    public void setRecordForced(boolean recordForced) {
        this.recordForced = recordForced;
    }

    public String generateSrc(String fileName, GraphQLSchema schema) throws ServiceTypesGenerationException {
        try {
            this.fileName = fileName;
//            populateSchemaNamedTypes(schema);
            String generatedSyntaxTree = Formatter.format(this.generateSyntaxTree(schema)).toString();
            return Formatter.format(generatedSyntaxTree);
        } catch (FormatterException e) {
            throw new ServiceTypesGenerationException(e.getMessage());
        }
    }

//    private void populateSchemaNamedTypes(GraphQLSchema schema) {
//        for (GraphQLNamedType type : schema.getAllTypesAsList()) {
//            if (type instanceof GraphQLObjectType) {
//                GraphQLObjectType objectType = (GraphQLObjectType) type;
//                schemaNamedTypes.get(Constants.GRAPHQL_OBJECT_TYPE).add(objectType);
//            } else if (type instanceof GraphQLInterfaceType) {
//                schemaNamedTypes.get(Constants.GRAPHQL_INTERFACE_TYPE).add(type);
//            } else if (type instanceof GraphQLInputObjectType) {
//                schemaNamedTypes.get(Constants.GRAPHQL_INPUT_OBJECT_TYPE).add(type);
//            } else if (type instanceof GraphQLEnumType) {
//                schemaNamedTypes.get(Constants.GRAPHQL_ENUM_TYPE).add(type);
//            } else if (type instanceof GraphQLUnionType) {
//                schemaNamedTypes.get(Constants.GRAPHQL_UNION_TYPE).add(type);
//            }
//        }
//
//    }

    public SyntaxTree generateSyntaxTree(GraphQLSchema schema)  {
        NodeList<ImportDeclarationNode> imports = generateImports();

        List<ModuleMemberDeclarationNode> moduleMembers = new LinkedList<>();
        addServiceObjectTypeDefinitionNode(schema, moduleMembers);
        addTypeDefinitions(schema, moduleMembers);
//        addInputTypeDefinitions(schema, moduleMembers);
//        addInterfaceTypeDefinitions(schema, moduleMembers);
//
//        if (recordForced) {
//            addTypesRecordForced(schema, moduleMembers);
//        } else {
//            addServiceClassTypes(schema, moduleMembers);
//        }
//
//        addEnumTypeDefinitions(schema, moduleMembers);
//        addUnionTypeDefinitions(schema, moduleMembers);
        NodeList<ModuleMemberDeclarationNode> moduleMemberNodes = createNodeList(moduleMembers);
        ModulePartNode modulePartNode =
                createModulePartNode(imports, moduleMemberNodes, createToken(SyntaxKind.EOF_TOKEN));

        TextDocument textDocument = TextDocuments.from(CodeGeneratorConstants.EMPTY_STRING);
        SyntaxTree syntaxTree = SyntaxTree.from(textDocument);
        return syntaxTree.modifyWith(modulePartNode);
    }

    private void addTypeDefinitions(GraphQLSchema schema, List<ModuleMemberDeclarationNode> moduleMembers) {
        HashMap<GraphQLObjectType, Boolean> mapObjectTypesToCheckRecordPossible =
                new LinkedHashMap<>();

        List<ModuleMemberDeclarationNode> inputObjectTypesModuleMembers = new ArrayList<>();
        List<ModuleMemberDeclarationNode> interfaceTypesModuleMembers = new ArrayList<>();
        List<ModuleMemberDeclarationNode> enumTypesModuleMembers = new ArrayList<>();
        List<ModuleMemberDeclarationNode> unionTypesModuleMembers = new ArrayList<>();
        List<ModuleMemberDeclarationNode> objectTypesModuleMembers = new ArrayList<>();

        Iterator<Map.Entry<String, GraphQLNamedType>> typesIterator = schema.getTypeMap().entrySet().iterator();
        while (typesIterator.hasNext()) {
            Map.Entry<String, GraphQLNamedType> typeEntry = typesIterator.next();
            String key = typeEntry.getKey();
            GraphQLNamedType type = typeEntry.getValue();
            if (type instanceof GraphQLInputObjectType) {
                GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) type;
//                moduleMembers.add(generateRecordType(inputObjectType));
                inputObjectTypesModuleMembers.add(generateRecordType(inputObjectType));
            }
            if (!key.startsWith("__") && type instanceof GraphQLInterfaceType) {
                GraphQLInterfaceType interfaceType = (GraphQLInterfaceType) type;
//                moduleMembers.add(generateInterfaceType(interfaceType));
                interfaceTypesModuleMembers.add(generateInterfaceType(interfaceType));
            }
            if (!key.startsWith("__") && type instanceof GraphQLEnumType) {
                GraphQLEnumType enumType = (GraphQLEnumType) type;
//                moduleMembers.add(generateEnumType(enumType));
                enumTypesModuleMembers.add(generateEnumType(enumType));
            }
            if (!key.startsWith("__") && type instanceof GraphQLUnionType) {
                GraphQLUnionType unionType = (GraphQLUnionType) type;
                for (GraphQLNamedOutputType namedOutputType : unionType.getTypes()) {
                    if (namedOutputType instanceof GraphQLObjectType ) {
                        GraphQLObjectType namedOutputObjectType = (GraphQLObjectType) namedOutputType;
                        if (mapObjectTypesToCheckRecordPossible.get(namedOutputObjectType) != null) {
                            mapObjectTypesToCheckRecordPossible.replace(namedOutputObjectType, false);
                        }
                        else {
                            mapObjectTypesToCheckRecordPossible.put(namedOutputObjectType, false);
                        }
                    }
                }
//                moduleMembers.add(generateUnionType(unionType));
                unionTypesModuleMembers.add(generateUnionType(unionType));
            }
            if (!key.startsWith("__") && !CodeGeneratorConstants.QUERY.equals(key) &&
                    !CodeGeneratorConstants.MUTATION.equals(key) && !CodeGeneratorConstants.SUBSCRIPTION.equals(key) &&
                    type instanceof GraphQLObjectType) {
                GraphQLObjectType objectType = (GraphQLObjectType) type;
                if (mapObjectTypesToCheckRecordPossible.get(objectType) == null) {
                    mapObjectTypesToCheckRecordPossible.put(objectType, true);
                }
                if (hasFieldsWithInputs(objectType) || objectType.getInterfaces().size()>0) {
                    mapObjectTypesToCheckRecordPossible.replace(objectType, false);
                }
            }
        }
        Iterator<Map.Entry<GraphQLObjectType, Boolean>> objectTypesToRecordCheckIterator =
                mapObjectTypesToCheckRecordPossible.entrySet().iterator();
        while (objectTypesToRecordCheckIterator.hasNext()) {
            Map.Entry<GraphQLObjectType, Boolean> next = objectTypesToRecordCheckIterator.next();
            GraphQLObjectType nextObjectType = next.getKey();
            Boolean isPossible = next.getValue();
            if (isPossible && recordForced) {
//                moduleMembers.add(generateRecordType(nextObjectType));
                objectTypesModuleMembers.add(generateRecordType(nextObjectType));
            } else {
//                moduleMembers.add(generateServiceClassType(nextObjectType));
                objectTypesModuleMembers.add(generateServiceClassType(nextObjectType));
            }
        }

        moduleMembers.addAll(inputObjectTypesModuleMembers);
        moduleMembers.addAll(interfaceTypesModuleMembers);
        moduleMembers.addAll(enumTypesModuleMembers);
        moduleMembers.addAll(unionTypesModuleMembers);
        moduleMembers.addAll(objectTypesModuleMembers);
    }

//    private void addInterfaceTypeDefinitions(GraphQLSchema schema, List<ModuleMemberDeclarationNode> moduleMembers) {
//        Iterator<Map.Entry<String, GraphQLNamedType>> typesIterator = schema.getTypeMap().entrySet().iterator();
//        while (typesIterator.hasNext()) {
//            Map.Entry<String, GraphQLNamedType> typeEntry = typesIterator.next();
//            String key = typeEntry.getKey();
//            GraphQLNamedType type = typeEntry.getValue();
//            if (!key.startsWith("__") && type instanceof GraphQLInterfaceType) {
//                GraphQLInterfaceType interfaceType = (GraphQLInterfaceType) type;
//                moduleMembers.add(generateInterfaceType(interfaceType));
//            }
//        }
//    }

    private ModuleMemberDeclarationNode generateInterfaceType(GraphQLInterfaceType interfaceType) {
        DistinctTypeDescriptorNode interfaceTypeDescriptorNode =
                createDistinctTypeDescriptorNode(createToken(SyntaxKind.DISTINCT_KEYWORD),
                        createObjectTypeDescriptorNode(createNodeList(createToken(SyntaxKind.SERVICE_KEYWORD)),
                                createToken(SyntaxKind.OBJECT_KEYWORD), createToken(SyntaxKind.OPEN_BRACE_TOKEN),
                                generateInterfaceTypeDescriptorMembers(interfaceType.getInterfaces(),
                                        interfaceType.getFields()), createToken(SyntaxKind.CLOSE_BRACE_TOKEN)));

        return createTypeDefinitionNode(null, null, createToken(SyntaxKind.TYPE_KEYWORD),
                createIdentifierToken(interfaceType.getName()), interfaceTypeDescriptorNode,
                createToken(SyntaxKind.SEMICOLON_TOKEN));
    }

//    private DistinctTypeDescriptorNode generateInterfaceTypeDescriptorNode(List<GraphQLFieldDefinition> fields) {
//        ObjectTypeDescriptorNode interfaceTypeDescriptor =
//                createObjectTypeDescriptorNode(createNodeList(createToken(SyntaxKind.SERVICE_KEYWORD)),
//                        createToken(SyntaxKind.OBJECT_KEYWORD), createToken(SyntaxKind.OPEN_BRACE_TOKEN),
//                        generateInterfaceTypeDescriptorMembers(interfaceType.getInterfaces(), fields), createToken
//                        (SyntaxKind.CLOSE_BRACE_TOKEN));
//        return createDistinctTypeDescriptorNode(createToken(SyntaxKind.DISTINCT_KEYWORD), interfaceTypeDescriptor);
//    }

//    private List<Node> generateMethodDeclarationsFor(List<GraphQLFieldDefinition> fields) {
//        List<Node> members = new LinkedList<>();
//        for (GraphQLFieldDefinition field : fields) {
//            NodeList<Token> methodQualifiers = createNodeList(createToken(SyntaxKind.RESOURCE_KEYWORD));
//
//            NodeList<Node> methodRelativeResourcePaths =
//                    createNodeList(createIdentifierToken(field.getDefinition().getName()));
//
//            SeparatedNodeList<ParameterNode> methodSignatureRequiredParams =
//                    generateMethodSignatureRequiredParams(field.getArguments());
//
//            ReturnTypeDescriptorNode methodSignatureReturnTypeDescriptor =
//                    generateMethodSignatureReturnTypeDescriptor(field.getType());
//
//            FunctionSignatureNode methodSignatureNode =
//                    createFunctionSignatureNode(createToken(SyntaxKind.OPEN_PAREN_TOKEN), methodSignatureRequiredParams,
//                            createToken(SyntaxKind.CLOSE_PAREN_TOKEN), methodSignatureReturnTypeDescriptor);
//
//            MethodDeclarationNode methodDeclaration =
//                    createMethodDeclarationNode(SyntaxKind.METHOD_DECLARATION, null, methodQualifiers,
//                            createToken(SyntaxKind.FUNCTION_KEYWORD), createIdentifierToken(CodeGeneratorConstants.GET),
//                            methodRelativeResourcePaths, methodSignatureNode, createToken(SyntaxKind.SEMICOLON_TOKEN));
//
//            members.add(methodDeclaration);
//        }
//        return members;
//    }

    private NodeList<Node> generateInterfaceTypeDescriptorMembers(List<GraphQLNamedOutputType> interfaces,
                                                                  List<GraphQLFieldDefinition> fields) {
        List<Node> members = new LinkedList<>();

        List<GraphQLNamedOutputType> filteredInterfaces = getInterfacesToBeWritten(interfaces);
        List<GraphQLFieldDefinition> filteredFields = getFieldsToBeWritten(fields, filteredInterfaces);

        for (GraphQLNamedOutputType filteredInterface : filteredInterfaces) {
            TypeReferenceNode filteredInterfaceTypeReferenceNode =
                    createTypeReferenceNode(createToken(SyntaxKind.ASTERISK_TOKEN),
                            createSimpleNameReferenceNode(createIdentifierToken(filteredInterface.getName())),
                            createToken(SyntaxKind.SEMICOLON_TOKEN));
            members.add(filteredInterfaceTypeReferenceNode);
        }

        for (GraphQLFieldDefinition field : filteredFields) {
            NodeList<Token> methodQualifiers = createNodeList(createToken(SyntaxKind.RESOURCE_KEYWORD));

            NodeList<Node> methodRelativeResourcePaths =
                    createNodeList(createIdentifierToken(field.getDefinition().getName()));

            SeparatedNodeList<ParameterNode> methodSignatureRequiredParams =
                    generateMethodSignatureRequiredParams(field.getArguments());

            ReturnTypeDescriptorNode methodSignatureReturnTypeDescriptor =
                    generateMethodSignatureReturnTypeDescriptor(field.getType());

            FunctionSignatureNode methodSignatureNode =
                    createFunctionSignatureNode(createToken(SyntaxKind.OPEN_PAREN_TOKEN), methodSignatureRequiredParams,
                            createToken(SyntaxKind.CLOSE_PAREN_TOKEN), methodSignatureReturnTypeDescriptor);

            MethodDeclarationNode methodDeclaration =
                    createMethodDeclarationNode(SyntaxKind.METHOD_DECLARATION, null, methodQualifiers,
                            createToken(SyntaxKind.FUNCTION_KEYWORD), createIdentifierToken(CodeGeneratorConstants.GET),
                            methodRelativeResourcePaths, methodSignatureNode, createToken(SyntaxKind.SEMICOLON_TOKEN));

            members.add(methodDeclaration);
        }
        return createNodeList(members);
    }

    private List<GraphQLFieldDefinition> getFieldsToBeWritten(List<GraphQLFieldDefinition> allFields,
                                                              List<GraphQLNamedOutputType> filteredInterfaces) {
        HashSet<String> fieldNames = new HashSet<>();
        for (GraphQLFieldDefinition field : allFields) {
            fieldNames.add(field.getName());
        }
        for (GraphQLNamedOutputType interfaceType : filteredInterfaces) {
            if (interfaceType instanceof GraphQLInterfaceType) {
                GraphQLInterfaceType graphQLInterfaceType = (GraphQLInterfaceType) interfaceType;
                for (GraphQLFieldDefinition field : graphQLInterfaceType.getFields()) {
                    if (fieldNames.contains(field.getName())) {
                        fieldNames.remove(field.getName());
                    }
                }
            }
        }
        List<GraphQLFieldDefinition> filteredFields = new ArrayList<>();
        for (GraphQLFieldDefinition field : allFields) {
            if (fieldNames.contains(field.getName())) {
                filteredFields.add(field);
            }
        }
        return filteredFields;
    }

    private List<GraphQLNamedOutputType> getInterfacesToBeWritten(List<GraphQLNamedOutputType> interfaceTypes) {
        HashSet<String> interfaceTypeNames = new HashSet<>();
        for (GraphQLNamedOutputType interfaceType : interfaceTypes) {
            interfaceTypeNames.add(interfaceType.getName());
        }
        for (GraphQLNamedOutputType interfaceType : interfaceTypes) {
            if (interfaceType instanceof GraphQLInterfaceType) {
                GraphQLInterfaceType graphQLInterfaceType = (GraphQLInterfaceType) interfaceType;
                for (GraphQLNamedOutputType subInterface : graphQLInterfaceType.getInterfaces()) {
                    if (interfaceTypeNames.contains(subInterface.getName())) {
                        interfaceTypeNames.remove(subInterface.getName());
                    }
                }
            }
        }
        List<GraphQLNamedOutputType> filteredInterfaces = new ArrayList<>();
        for (GraphQLNamedOutputType interfaceType : interfaceTypes) {
            if (interfaceTypeNames.contains(interfaceType.getName())) {
                filteredInterfaces.add(interfaceType);
            }
        }
        return filteredInterfaces;
    }

    private void addUnionTypeDefinitions(GraphQLSchema schema, List<ModuleMemberDeclarationNode> moduleMembers) {
        Iterator<Map.Entry<String, GraphQLNamedType>> typesIterator = schema.getTypeMap().entrySet().iterator();

        while (typesIterator.hasNext()) {
            Map.Entry<String, GraphQLNamedType> typeEntry = typesIterator.next();
            String key = typeEntry.getKey();
            GraphQLNamedType type = typeEntry.getValue();
            if (!key.startsWith("__") && type instanceof GraphQLUnionType) {
                GraphQLUnionType unionType = (GraphQLUnionType) type;
                moduleMembers.add(generateUnionType(unionType));
            }
        }
    }

    private ModuleMemberDeclarationNode generateUnionType(GraphQLUnionType unionType) {
        UnionTypeDescriptorNode unionTypeDescriptorNode = generateUnionTypeDescriptorNode(unionType.getTypes());

        return createTypeDefinitionNode(null, null, createToken(SyntaxKind.TYPE_KEYWORD),
                createIdentifierToken(unionType.getName()), unionTypeDescriptorNode,
                createToken(SyntaxKind.SEMICOLON_TOKEN));
    }

    private UnionTypeDescriptorNode generateUnionTypeDescriptorNode(List<GraphQLNamedOutputType> types) {
        if (types.size() == 2) {
            String type1Name = types.get(0).getName();
            String type2Name = types.get(1).getName();
            return createUnionTypeDescriptorNode(createSimpleNameReferenceNode(createIdentifierToken(type1Name)),
                    createToken(SyntaxKind.PIPE_TOKEN),
                    createSimpleNameReferenceNode(createIdentifierToken(type2Name)));
        }
        List<GraphQLNamedOutputType> typesSubList = types.subList(0, types.size() - 1);
        String lastTypeName = types.get(types.size() - 1).getName();
        return createUnionTypeDescriptorNode(generateUnionTypeDescriptorNode(typesSubList),
                createToken(SyntaxKind.PIPE_TOKEN), createSimpleNameReferenceNode(createIdentifierToken(lastTypeName)));
    }

    private void addEnumTypeDefinitions(GraphQLSchema schema, List<ModuleMemberDeclarationNode> typeDefinitionNodes) {
        Iterator<Map.Entry<String, GraphQLNamedType>> typesIterator = schema.getTypeMap().entrySet().iterator();

        while (typesIterator.hasNext()) {
            Map.Entry<String, GraphQLNamedType> typeEntry = typesIterator.next();
            String key = typeEntry.getKey();
            GraphQLNamedType type = typeEntry.getValue();
            if (!key.startsWith("__") && type instanceof GraphQLEnumType) {
                GraphQLEnumType enumType = (GraphQLEnumType) type;
                typeDefinitionNodes.add(generateEnumType(enumType));
            }
        }
    }

    private ModuleMemberDeclarationNode generateEnumType(GraphQLEnumType enumType) {
        List<Node> enumMembers = new ArrayList<>();
        List<GraphQLEnumValueDefinition> enumValues = enumType.getValues();
        for (int i = 0; i < enumValues.size(); i++) {
            GraphQLEnumValueDefinition enumValue = enumValues.get(i);
            EnumMemberNode enumMember =
                    createEnumMemberNode(null, createIdentifierToken(enumValue.getName()), null, null);
            if (i == enumValues.size() - 1) {
                enumMembers.add(enumMember);
            } else {
                enumMembers.add(enumMember);
                enumMembers.add(createToken(SyntaxKind.COMMA_TOKEN));
            }
        }
        SeparatedNodeList<Node> enumMemberNodes = createSeparatedNodeList(enumMembers);
        return createEnumDeclarationNode(null, null, createToken(SyntaxKind.ENUM_KEYWORD),
                createIdentifierToken(enumType.getName()), createToken(SyntaxKind.OPEN_BRACE_TOKEN), enumMemberNodes,
                createToken(SyntaxKind.CLOSE_BRACE_TOKEN), null);
    }


    private void addInputTypeDefinitions(GraphQLSchema schema, List<ModuleMemberDeclarationNode> typeDefinitionNodes) {
        Iterator<Map.Entry<String, GraphQLNamedType>> typesIterator = schema.getTypeMap().entrySet().iterator();

        while (typesIterator.hasNext()) {
            Map.Entry<String, GraphQLNamedType> typeEntry = typesIterator.next();
            String key = typeEntry.getKey();
            GraphQLNamedType type = typeEntry.getValue();
            if (type instanceof GraphQLInputObjectType) {
                GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) type;
                typeDefinitionNodes.add(generateRecordType(inputObjectType));
            }
        }

    }

    private ModuleMemberDeclarationNode generateRecordType(GraphQLInputObjectType type) {
        List<GraphQLInputObjectField> typeFields = type.getFields();

        NodeList<Node> recordTypeFields = generateRecordTypeFieldsForGraphQLInputObjectFields(typeFields);
        RecordTypeDescriptorNode recordTypeDescriptor =
                createRecordTypeDescriptorNode(createToken(SyntaxKind.RECORD_KEYWORD),
                        createToken(SyntaxKind.OPEN_BRACE_PIPE_TOKEN), recordTypeFields, null,
                        createToken(SyntaxKind.CLOSE_BRACE_PIPE_TOKEN));
        TypeDefinitionNode typeDefinition = createTypeDefinitionNode(null, null, createToken(SyntaxKind.TYPE_KEYWORD),
                createIdentifierToken(type.getName()), recordTypeDescriptor, createToken(SyntaxKind.SEMICOLON_TOKEN));
        return typeDefinition;
    }

    private ModuleMemberDeclarationNode generateRecordType(GraphQLObjectType type) {
        List<GraphQLFieldDefinition> typeFields = type.getFields();
        NodeList<Node> recordTypeFields = generateRecordTypeFieldsForGraphQLFieldDefinitions(typeFields);

        RecordTypeDescriptorNode recordTypeDescriptor =
                createRecordTypeDescriptorNode(createToken(SyntaxKind.RECORD_KEYWORD),
                        createToken(SyntaxKind.OPEN_BRACE_TOKEN), recordTypeFields, null,
                        createToken(SyntaxKind.CLOSE_BRACE_TOKEN));
        TypeDefinitionNode typeDefinition = createTypeDefinitionNode(null, null, createToken(SyntaxKind.TYPE_KEYWORD),
                createIdentifierToken(type.getName()), recordTypeDescriptor, createToken(SyntaxKind.SEMICOLON_TOKEN));
        return typeDefinition;
    }

    private List<GraphQLInputObjectField> convertToGraphQLInputObjectFields(
            List<GraphQLSchemaElement> fieldDefinitions) {
        List<GraphQLInputObjectField> inputObjectFields = new ArrayList<>();
        for (GraphQLSchemaElement fieldDefinition : fieldDefinitions) {
            if (fieldDefinition instanceof GraphQLInputObjectField) {
                inputObjectFields.add((GraphQLInputObjectField) fieldDefinition);
            }
        }
        return inputObjectFields;
    }

    private NodeList<Node> generateRecordTypeFieldsForGraphQLInputObjectFields(
            List<GraphQLInputObjectField> inputTypeFields) {
        List<Node> fields = new ArrayList<>();
        for (GraphQLInputObjectField field : inputTypeFields) {
            fields.add(createRecordFieldNode(null, null, generateTypeDescriptor(field.getType()),
                    createIdentifierToken(field.getName()), null, createToken(SyntaxKind.SEMICOLON_TOKEN)));
        }
        return createNodeList(fields);
    }

    private NodeList<Node> generateRecordTypeFieldsForGraphQLFieldDefinitions(
            List<GraphQLFieldDefinition> typeInputFields) {
        List<Node> fields = new ArrayList<>();
        for (GraphQLFieldDefinition field : typeInputFields) {
            fields.add(createRecordFieldNode(null, null, generateTypeDescriptor(field.getType()),
                    createIdentifierToken(field.getName()), null, createToken(SyntaxKind.SEMICOLON_TOKEN)));
        }
        return createNodeList(fields);
    }


    private NodeList<Node> generateRecordTypeFields(List<GraphQLFieldDefinition> typeFields) {
        List<Node> fields = new ArrayList<>();
//        for (GraphQLFieldDefinition field : typeInputFields) {
//            fields.add(createRecordFieldNode(null, null, generateTypeDescriptor(field.getType()),
//                    createIdentifierToken(field.getName()), null,
//                    createToken(SyntaxKind.SEMICOLON_TOKEN)));
//        }
        return createNodeList(fields);
    }


    private void addTypesRecordForced(GraphQLSchema schema, List<ModuleMemberDeclarationNode> typeDefinitionNodes) {
        Iterator<Map.Entry<String, GraphQLNamedType>> typesIterator = schema.getTypeMap().entrySet().iterator();

        while (typesIterator.hasNext()) {
            Map.Entry<String, GraphQLNamedType> typeEntry = typesIterator.next();
            String key = typeEntry.getKey();
            GraphQLNamedType type = typeEntry.getValue();

            if (!key.startsWith("__") && !CodeGeneratorConstants.QUERY.equals(key) &&
                    !CodeGeneratorConstants.MUTATION.equals(key) && !CodeGeneratorConstants.SUBSCRIPTION.equals(key) &&
                    type instanceof GraphQLObjectType) {
                GraphQLObjectType objectType = (GraphQLObjectType) type;
                if (!hasFieldsWithInputs(objectType) && !isSubTypeOfAUnion(schema, objectType)) {
                    typeDefinitionNodes.add(generateRecordType(objectType));
                } else {
                    typeDefinitionNodes.add(generateServiceClassType(objectType));
                }
            }
        }
    }

    private boolean isSubTypeOfAUnion(GraphQLSchema schema, GraphQLObjectType objectType) {
        Iterator<Map.Entry<String, GraphQLNamedType>> typesIterator = schema.getTypeMap().entrySet().iterator();

        while (typesIterator.hasNext()) {
            Map.Entry<String, GraphQLNamedType> typeEntry = typesIterator.next();
            String key = typeEntry.getKey();
            GraphQLNamedType type = typeEntry.getValue();

            if (type instanceof GraphQLUnionType) {
                GraphQLUnionType unionType = (GraphQLUnionType) type;
                if (unionType.getTypes().contains(objectType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasFieldsWithInputs(GraphQLObjectType type) {
        List<GraphQLFieldDefinition> fields = type.getFields();
        for (GraphQLFieldDefinition field : fields) {
            if (field.getArguments().size() > 0) {
                return true;
            }
        }
        return false;
    }

    private void addServiceClassTypes(GraphQLSchema schema, List<ModuleMemberDeclarationNode> typeDefinitionNodes) {
        Iterator<Map.Entry<String, GraphQLNamedType>> typesIterator = schema.getTypeMap().entrySet().iterator();

        while (typesIterator.hasNext()) {
            Map.Entry<String, GraphQLNamedType> typeEntry = typesIterator.next();
            String key = typeEntry.getKey();
            GraphQLNamedType type = typeEntry.getValue();

            // TODO: generate record type for input type
            if (!key.startsWith("__") && !CodeGeneratorConstants.QUERY.equals(key) &&
                    !CodeGeneratorConstants.MUTATION.equals(key) && !CodeGeneratorConstants.SUBSCRIPTION.equals(key) &&
                    type instanceof GraphQLObjectType) {
                GraphQLObjectType objectType = (GraphQLObjectType) type;
                typeDefinitionNodes.add(generateServiceClassType(objectType));
            }
        }
    }

    private ClassDefinitionNode generateServiceClassType(GraphQLObjectType type) {
        NodeList<Token> classTypeQualifiers = generateServiceClassTypeQualifiers(type.getInterfaces().size() > 0);

        List<Node> serviceClassTypeMembersList = new ArrayList<>();
        for (GraphQLNamedOutputType typeInterface : getInterfacesToBeWritten(type.getInterfaces())) {
            serviceClassTypeMembersList.add(createTypeReferenceNode(createToken(SyntaxKind.ASTERISK_TOKEN),
                    createSimpleNameReferenceNode(createIdentifierToken(typeInterface.getName())),
                    createToken(SyntaxKind.SEMICOLON_TOKEN)));
        }

        for (Node fieldMember : generateServiceClassTypeMembers(type.getFields())) {
            serviceClassTypeMembersList.add(fieldMember);
        }
        NodeList<Node> serviceClassTypeMembers = createNodeList(serviceClassTypeMembersList);

        ClassDefinitionNode classDefinition =
                createClassDefinitionNode(null, null, classTypeQualifiers, createToken(SyntaxKind.CLASS_KEYWORD),
                        createIdentifierToken(type.getName()), createToken(SyntaxKind.OPEN_BRACE_TOKEN),
                        serviceClassTypeMembers, createToken(SyntaxKind.CLOSE_BRACE_TOKEN), null);

        return classDefinition;
    }

    private NodeList<Token> generateServiceClassTypeQualifiers(boolean isImplements) {
        List<Token> typeQualifierTokens = new ArrayList<>();
        if (isImplements) {
            typeQualifierTokens.add(createToken(SyntaxKind.DISTINCT_KEYWORD));
        }
        typeQualifierTokens.add(createToken(SyntaxKind.SERVICE_KEYWORD));
        return createNodeList(typeQualifierTokens);
    }

    private NodeList<Node> generateServiceClassTypeMembers(List<GraphQLFieldDefinition> fieldDefinitions) {
        List<Node> members = new ArrayList<>();

        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            FunctionDefinitionNode functionDefinition = generateServiceClassTypeMember(fieldDefinition);
            members.add(functionDefinition);
        }

        return createNodeList(members);
    }

    private FunctionDefinitionNode generateServiceClassTypeMember(GraphQLFieldDefinition fieldDefinition) {
        NodeList<Token> memberQualifiers = createNodeList(createToken(SyntaxKind.RESOURCE_KEYWORD));

        NodeList<Node> memberRelativeResourcePaths = createNodeList(createIdentifierToken(fieldDefinition.getName()));

        SeparatedNodeList<ParameterNode> methodSignatureParameters =
                generateMethodSignatureRequiredParams(fieldDefinition.getArguments());

        ReturnTypeDescriptorNode methodSignatureReturnTypeDescriptor =
                generateMethodSignatureReturnTypeDescriptor(fieldDefinition.getType());

        FunctionSignatureNode functionSignature =
                createFunctionSignatureNode(createToken(SyntaxKind.OPEN_PAREN_TOKEN), methodSignatureParameters,
                        createToken(SyntaxKind.CLOSE_PAREN_TOKEN), methodSignatureReturnTypeDescriptor);

        FunctionBodyBlockNode functionBody =
                createFunctionBodyBlockNode(createToken(SyntaxKind.OPEN_BRACE_TOKEN), null, createEmptyNodeList(),
                        createToken(SyntaxKind.CLOSE_BRACE_TOKEN), null);

        FunctionDefinitionNode functionDefinition =
                createFunctionDefinitionNode(SyntaxKind.RESOURCE_ACCESSOR_DEFINITION, null, memberQualifiers,
                        createToken(SyntaxKind.FUNCTION_KEYWORD), createIdentifierToken(CodeGeneratorConstants.GET),
                        memberRelativeResourcePaths, functionSignature, functionBody);

        return functionDefinition;
    }

    private void addServiceObjectTypeDefinitionNode(GraphQLSchema schema,
                                                    List<ModuleMemberDeclarationNode> typeDefinitionNodes) {
        ObjectTypeDescriptorNode serviceObjectTypeDescriptor =
                createObjectTypeDescriptorNode(createNodeList(createToken(SyntaxKind.SERVICE_KEYWORD)),
                        createToken(SyntaxKind.OBJECT_KEYWORD), createToken(SyntaxKind.OPEN_BRACE_TOKEN),
                        generateServiceObjectTypeMembers(schema), createToken(SyntaxKind.CLOSE_BRACE_TOKEN));
        TypeDefinitionNode serviceObjectTypeDefinition =
                createTypeDefinitionNode(null, null, createToken(SyntaxKind.TYPE_KEYWORD),
                        createIdentifierToken(this.fileName), serviceObjectTypeDescriptor,
                        createToken(SyntaxKind.SEMICOLON_TOKEN));
        typeDefinitionNodes.add(serviceObjectTypeDefinition);
    }

    private NodeList<Node> generateServiceObjectTypeMembers(GraphQLSchema schema) {
        List<Node> members = new ArrayList<>();

        TypeReferenceNode typeReferenceNode = createTypeReferenceNode(createToken(SyntaxKind.ASTERISK_TOKEN),
                createIdentifierToken(CodeGeneratorConstants.GRAPHQL_SERVICE_TYPE_NAME),
                createToken(SyntaxKind.SEMICOLON_TOKEN));
        members.add(typeReferenceNode);

        List<Node> serviceTypeMethodDeclarations =
                generateServiceTypeMethodDeclarations(schema.getQueryType(), schema.getMutationType(),
                        schema.getSubscriptionType());
        members.addAll(serviceTypeMethodDeclarations);

        return createNodeList(members);

    }

    // TODO: shorten the method
    private List<Node> generateServiceTypeMethodDeclarations(GraphQLObjectType queryType,
                                                             GraphQLObjectType mutationType,
                                                             GraphQLObjectType subscriptionType) {
        List<Node> methodDeclarations = new ArrayList<>();

        for (GraphQLFieldDefinition fieldDefinition : queryType.getFieldDefinitions()) {
            FunctionSignatureNode methodSignatureNode =
                    createFunctionSignatureNode(createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                            generateMethodSignatureRequiredParams(fieldDefinition.getArguments()),
                            createToken(SyntaxKind.CLOSE_PAREN_TOKEN),
                            generateMethodSignatureReturnTypeDescriptor(fieldDefinition.getType()));

            MethodDeclarationNode methodDeclaration = createMethodDeclarationNode(SyntaxKind.METHOD_DECLARATION, null,
                    createNodeList(createToken(SyntaxKind.RESOURCE_KEYWORD)), createToken(SyntaxKind.FUNCTION_KEYWORD),
                    createIdentifierToken(CodeGeneratorConstants.GET),
                    createNodeList(createIdentifierToken(fieldDefinition.getName())), methodSignatureNode,
                    createToken(SyntaxKind.SEMICOLON_TOKEN));

            methodDeclarations.add(methodDeclaration);
        }

        if (mutationType != null) {
            for (GraphQLFieldDefinition fieldDefinition : mutationType.getFieldDefinitions()) {
                FunctionSignatureNode methodSignatureNode =
                        createFunctionSignatureNode(createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                                generateMethodSignatureRequiredParams(fieldDefinition.getArguments()),
                                createToken(SyntaxKind.CLOSE_PAREN_TOKEN),
                                generateMethodSignatureReturnTypeDescriptor(fieldDefinition.getType(), false));

                MethodDeclarationNode methodDeclaration =
                        createMethodDeclarationNode(SyntaxKind.RESOURCE_ACCESSOR_DECLARATION, null,
                                createNodeList(createToken(SyntaxKind.REMOTE_KEYWORD)),
                                createToken(SyntaxKind.FUNCTION_KEYWORD), createIdentifierToken(""),
                                createNodeList(createIdentifierToken(fieldDefinition.getName())), methodSignatureNode,
                                createToken(SyntaxKind.SEMICOLON_TOKEN));

                methodDeclarations.add(methodDeclaration);
            }
        }

        if (subscriptionType != null) {
            for (GraphQLFieldDefinition fieldDefinition : subscriptionType.getFieldDefinitions()) {
                FunctionSignatureNode methodSignatureNode =
                        createFunctionSignatureNode(createToken(SyntaxKind.OPEN_PAREN_TOKEN),
                                generateMethodSignatureRequiredParams(fieldDefinition.getArguments()),
                                createToken(SyntaxKind.CLOSE_PAREN_TOKEN),
                                generateMethodSignatureReturnTypeDescriptor(fieldDefinition.getType(), true));

                MethodDeclarationNode methodDeclaration =
                        createMethodDeclarationNode(SyntaxKind.RESOURCE_ACCESSOR_DECLARATION, null,
                                createNodeList(createToken(SyntaxKind.RESOURCE_KEYWORD)),
                                createToken(SyntaxKind.FUNCTION_KEYWORD),
                                createIdentifierToken(CodeGeneratorConstants.SUBSCRIBE),
                                createNodeList(createIdentifierToken(fieldDefinition.getDefinition().getName())),
                                methodSignatureNode, createToken(SyntaxKind.SEMICOLON_TOKEN));

                methodDeclarations.add(methodDeclaration);
            }
        }
        return methodDeclarations;
    }

    private ReturnTypeDescriptorNode generateMethodSignatureReturnTypeDescriptor(GraphQLOutputType type,
                                                                                 boolean isStream) {
        TypeDescriptorNode typeDescriptor = generateTypeDescriptor(type, false);
        if (isStream) {
            return createReturnTypeDescriptorNode(createToken(SyntaxKind.RETURNS_KEYWORD), createEmptyNodeList(),
                    createStreamTypeDescriptorNode(createToken(SyntaxKind.STREAM_KEYWORD),
                            createStreamTypeParamsNode(createToken(SyntaxKind.LT_TOKEN), typeDescriptor, null, null,
                                    createToken(SyntaxKind.GT_TOKEN))));
        } else {

            return createReturnTypeDescriptorNode(createToken(SyntaxKind.RETURNS_KEYWORD), createEmptyNodeList(),
                    typeDescriptor);
        }
    }

    private ReturnTypeDescriptorNode generateMethodSignatureReturnTypeDescriptor(GraphQLOutputType type) {
        return generateMethodSignatureReturnTypeDescriptor(type, false);
    }

    private SeparatedNodeList<ParameterNode> generateMethodSignatureRequiredParams(List<GraphQLArgument> arguments) {
        List<Node> requiredParams = new ArrayList<>();

        for (int i = 0; i < arguments.size(); i++) {
            GraphQLArgument argument = arguments.get(i);
            GraphQLInputType argumentType = argument.getType();
            TypeDescriptorNode argumentTypeDescriptor = generateTypeDescriptor(argumentType, false);

            RequiredParameterNode requiredParameterNode =
                    createRequiredParameterNode(createEmptyNodeList(), argumentTypeDescriptor,
                            createIdentifierToken(argument.getName()));

            requiredParams.add(requiredParameterNode);

            if (i != arguments.size() - 1) {
                requiredParams.add(createToken(SyntaxKind.COMMA_TOKEN));
            }
        }

        return createSeparatedNodeList(requiredParams);
    }

    // TODO: check into the casting
    // TODO: check restraining the input param type as possible
    private TypeDescriptorNode generateTypeDescriptor(GraphQLSchemaElement argumentType, boolean nonNull) {
        if (argumentType instanceof GraphQLScalarType) {
            GraphQLScalarType scalarArgumentType = (GraphQLScalarType) argumentType;
            if (nonNull) {
                return createBuiltinSimpleNameReferenceNode(getTypeDescFor(scalarArgumentType.getName()),
                        createToken(getTypeKeywordFor(scalarArgumentType.getName())));
            } else {
                return createOptionalTypeDescriptorNode(
                        createBuiltinSimpleNameReferenceNode(getTypeDescFor(scalarArgumentType.getName()),
                                createToken(getTypeKeywordFor(scalarArgumentType.getName()))),
                        createToken(SyntaxKind.QUESTION_MARK_TOKEN));
            }
        }
        if (argumentType instanceof GraphQLObjectType) {
            GraphQLObjectType objectArgumentType = (GraphQLObjectType) argumentType;
            if (nonNull) {
                return createSimpleNameReferenceNode(createIdentifierToken(objectArgumentType.getName()));
            } else {
                return createOptionalTypeDescriptorNode(
                        createSimpleNameReferenceNode(createIdentifierToken(objectArgumentType.getName())),
                        createToken(SyntaxKind.QUESTION_MARK_TOKEN));
            }
        }
        if (argumentType instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType inputObjectArgumentType = (GraphQLInputObjectType) argumentType;
            if (nonNull) {
                return createSimpleNameReferenceNode(createIdentifierToken(inputObjectArgumentType.getName()));
            } else {
                return createOptionalTypeDescriptorNode(
                        createSimpleNameReferenceNode(createIdentifierToken(inputObjectArgumentType.getName())),
                        createToken(SyntaxKind.QUESTION_MARK_TOKEN));
            }
        }

        if (argumentType instanceof GraphQLNonNull) {
            nonNull = true;
            GraphQLNonNull nonNullArgumentType = (GraphQLNonNull) argumentType;
            return generateTypeDescriptor(nonNullArgumentType.getWrappedType(), nonNull);
        }

        if (argumentType instanceof GraphQLList) {
            GraphQLList listArgumentType = (GraphQLList) argumentType;

            ArrayDimensionNode arrayDimensionNode =
                    createArrayDimensionNode(createToken(SyntaxKind.OPEN_BRACKET_TOKEN), null,
                            createToken(SyntaxKind.CLOSE_BRACKET_TOKEN));
            TypeDescriptorNode wrappedArgumentTypeDescriptor =
                    generateTypeDescriptor(listArgumentType.getWrappedType(), nonNull);
            ArrayTypeDescriptorNode arrayTypeDescriptorNode =
                    createArrayTypeDescriptorNode(wrappedArgumentTypeDescriptor, createNodeList(arrayDimensionNode));

            if (nonNull) {
                return arrayTypeDescriptorNode;
            } else {
                return createOptionalTypeDescriptorNode(arrayTypeDescriptorNode,
                        createToken(SyntaxKind.QUESTION_MARK_TOKEN));
            }
        }
        return null;
    }

    private TypeDescriptorNode generateTypeDescriptor(GraphQLSchemaElement argumentType) {
        return generateTypeDescriptor(argumentType, false);
    }

    private SyntaxKind getTypeKeywordFor(String argumentTypeName) {
        // TODO: Handle exceptions
        if (Constants.GRAPHQL_STRING_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.STRING_KEYWORD;
        } else if (Constants.GRAPHQL_INT_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.INT_KEYWORD;
        } else if (Constants.GRAPHQL_FLOAT_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.FLOAT_KEYWORD;
        } else if (Constants.GRAPHQL_BOOLEAN_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.BOOLEAN_KEYWORD;
        } else if (Constants.GRAPHQL_ID_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.STRING_KEYWORD;
        } else {
            return SyntaxKind.STREAM_KEYWORD;
        }
    }

    private SyntaxKind getTypeDescFor(String argumentTypeName) {
        // TODO: Handle exceptions
        if (Constants.GRAPHQL_STRING_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.STRING_TYPE_DESC;
        } else if (Constants.GRAPHQL_INT_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.INT_TYPE_DESC;
        } else if (Constants.GRAPHQL_FLOAT_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.FLOAT_TYPE_DESC;
        } else if (Constants.GRAPHQL_BOOLEAN_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.BOOLEAN_TYPE_DESC;
        } else if (Constants.GRAPHQL_ID_TYPE.equals(argumentTypeName)) {
            return SyntaxKind.STRING_TYPE_DESC;
        } else {
            return SyntaxKind.STRING_TYPE_DESC;
        }
    }

    private NodeList<ImportDeclarationNode> generateImports() {
        List<ImportDeclarationNode> imports = new ArrayList<>();
        ImportDeclarationNode importForGraphQL =
                CodeGeneratorUtils.getImportDeclarationNode(CodeGeneratorConstants.BALLERINA,
                        CodeGeneratorConstants.GRAPHQL);
        imports.add(importForGraphQL);
        return createNodeList(imports);
    }

}
