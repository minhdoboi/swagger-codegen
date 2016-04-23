package io.swagger.codegen.languages;

import io.swagger.codegen.*;
import io.swagger.models.properties.*;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Swagger;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.io.File;
import java.util.stream.Collectors;

public class HaskellServantCodegen extends DefaultCodegen implements CodegenConfig {

  // source folder where to write the files
  protected String sourceFolder = "src";
  protected String apiVersion = "0.0.1";
  public static final String APPEND_HEADER = "appendHeader";
  public Optional<String> supplementalHeader = Optional.empty();

  /**
   * Configures the type of generator.
   *
   * @return  the CodegenType for this generator
   * @see     io.swagger.codegen.CodegenType
   */
  public CodegenType getTag() {
    return CodegenType.SERVER;
  }

  /**
   * Configures a friendly name for the generator.  This will be used by the generator
   * to select the library with the -l flag.
   *
   * @return the friendly name for the generator
   */
  public String getName() {
    return "haskell-servant";
  }

  /**
   * Returns human-friendly help for the generator.  Provide the consumer with help
   * tips, parameters here
   *
   * @return A string value for the help message
   */
  public String getHelp() {
    return "Generates a HaskellServantCodegen library.";
  }

  public HaskellServantCodegen() {
    super();

    // set the output folder here
    outputFolder = "generated-code/HaskellServantCodegen";

    /**
     * Models.  You can write model files using the modelTemplateFiles map.
     * if you want to create one template for file, you can do so here.
     * for multiple files for model, just put another entry in the `modelTemplateFiles` with
     * a different extension
     */
    modelTemplateFiles.put(
      "model.mustache", // the template to use
      ".hs");       // the extension for each file to write

    /**
     * Api classes.  You can write classes for each Api file with the apiTemplateFiles map.
     * as with models, add multiple entries with different extensions for multiple files per
     * class
     */
    apiTemplateFiles.put(
      "api.mustache",   // the template to use
      ".hs");       // the extension for each file to write

    /**
     * Template Location.  This is the location which templates will be read from.  The generator
     * will use the resource stream to attempt to read the templates.
     */
    embeddedTemplateDir = templateDir = "haskell-servant";

    /**
     * Api Package.  Optional, if needed, this can be used in templates
     */
    apiPackage = "Api";

    /**
     * Model Package.  Optional, if needed, this can be used in templates
     */
    modelPackage = "Model";

    /**
     * Reserved words.  Override this with reserved words specific to your language
     */
    // from https://wiki.haskell.org/Keywords
    setReservedWordsLowerCase(
        Arrays.asList(
            "as", "case", "of",
            "class", "data", // "data family", "data instance",
            "default", "deriving", // "deriving instance",
            "do",
            "forall", "foreign", "hiding",
            "id",
            "if", "then", "else",
            "import", "infix", "infixl", "infixr",
            "instance", "let", "in",
            "mdo", "module", "newtype",
            "proc", "qualified", "rec",
            "type", // "type family", "type instance",
            "where"
        )
    );

    /**
     * Additional Properties.  These values can be passed to the templates and
     * are available in models, apis, and supporting files
     */
    additionalProperties.put("apiVersion", apiVersion);

    /**
     * Supporting Files.  You can write single files for the generator with the
     * entire object tree available.  If the input file has a suffix of `.mustache
     * it will be processed by the template engine.  Otherwise, it will be copied
     */
    supportingFiles.add(new SupportingFile("stack.mustache", "", "stack.yaml"));
    supportingFiles.add(new SupportingFile("Setup.mustache", "", "Setup.hs"));

    /**
     * Language Specific Primitives.  These types will not trigger imports by
     * the client generator
     */
    languageSpecificPrimitives = new HashSet<String>(
      Arrays.asList(
        "Bool",
        "String",
        "Int",
        "Integer",
        "Float",
        "Char",
        "Double",
        "List",
        "FilePath")
    );

    typeMapping.clear();
    // typeMapping.put("enum", "NSString");
    typeMapping.put("array", "List");
    typeMapping.put("set", "Set");
    typeMapping.put("boolean", "Bool");
    typeMapping.put("string", "Text");
    typeMapping.put("int", "Int");
    typeMapping.put("long", "Integer");
    typeMapping.put("float", "Float");
    // typeMapping.put("byte", "Byte");
    typeMapping.put("short", "Int");
    typeMapping.put("char", "Char");
    typeMapping.put("double", "Double");
    typeMapping.put("DateTime", "Int");
    // typeMapping.put("object", "Map");
    typeMapping.put("file", "FilePath");

    importMapping.clear();
    importMapping.put("Map", "qualified Data.Map as Map");
    importMapping.put("Text", "Data.Text");
    importMapping.put("Int", "Data.Int");

    cliOptions.add(new CliOption(CodegenConstants.MODEL_PACKAGE, CodegenConstants.MODEL_PACKAGE_DESC));
    cliOptions.add(new CliOption(CodegenConstants.API_PACKAGE, CodegenConstants.API_PACKAGE_DESC));
    cliOptions.add(new CliOption(APPEND_HEADER, "append header argument"));
  }

  @Override
  public void processOpts() {
    super.processOpts();
    if (additionalProperties.containsKey(APPEND_HEADER)) {
      supplementalHeader = Optional.of(additionalProperties.get(APPEND_HEADER).toString());
    }
  }

  @Override
  public void preprocessSwagger(Swagger swagger) {
    // From the title, compute a reasonable name for the package and the API
    String title = swagger.getInfo().getTitle();

    // Drop any API suffix
    if(title == null) {
      title = "Swagger";
    } else {
      title = title.trim();
      if (title.toUpperCase().endsWith("API")) {
        title = title.substring(0, title.length() - 3);
      }
    }

    String[] words = title.split(" ");

    // The package name is made by appending the lowercased words of the title interspersed with dashes
    List<String> wordsLower = new ArrayList<String>();
    for (String word : words) {
      wordsLower.add(word.toLowerCase());
    }
    String cabalName = wordsLower.stream().collect(Collectors.joining("-"));

    // The API name is made by appending the capitalized words of the title
    List<String> wordsCaps = new ArrayList<String>();
    for (String word : words) {
      wordsCaps.add(word.substring(0, 1).toUpperCase() + word.substring(1));
    }
    String apiName = wordsCaps.stream().collect(Collectors.joining());

    // Set the filenames to write for the API
    supportingFiles.add(new SupportingFile("haskell-servant-codegen.mustache", "", cabalName + ".cabal"));
    supportingFiles.add(new SupportingFile("Apis.mustache", "lib/"+ apiPackage(), "Apis.hs"));
    supportingFiles.add(new SupportingFile("Utils.mustache", "lib/"+ apiPackage(), "Utils.hs"));


    additionalProperties.put("title", apiName);
    additionalProperties.put("titleLower", apiName.substring(0, 1).toLowerCase() + apiName.substring(1));
    additionalProperties.put("cabalPackage", cabalName);

    // Due to the way servant resolves types, we need a high context stack limit
    additionalProperties.put("contextStackLimit", swagger.getPaths().size() * 2 + 300);

    super.preprocessSwagger(swagger);
  }

  /**
   * Escapes a reserved word as defined in the `reservedWords` array. Handle escaping
   * those terms here.  This logic is only called if a variable matches the reseved words
   *
   * @return the escaped term
   */
  @Override
  public String escapeReservedWord(String name) {
    return name + "_";
  }

  /**
   * Location to write model files.  You can use the modelPackage() as defined when the class is
   * instantiated
   */
  public String modelFileFolder() {
    return outputFolder + File.separatorChar + "lib" + File.separatorChar + modelPackage().replace('.', File.separatorChar);
  }

  /**
   * Location to write api files.  You can use the apiPackage() as defined when the class is
   * instantiated
   */
  @Override
  public String apiFileFolder() {
    return outputFolder + File.separatorChar + "lib" + File.separatorChar + apiPackage().replace('.', File.separatorChar);
  }

  /**
   * Optional - type declaration.  This is a String which is used by the templates to instantiate your
   * types.  There is typically special handling for different property types
   *
   * @return a string value used as the `dataType` field for model templates, `returnType` for api templates
   */
  @Override
  public String getTypeDeclaration(Property p) {
    if(p instanceof ArrayProperty) {
      ArrayProperty ap = (ArrayProperty) p;
      Property inner = ap.getItems();
      return "[" + toModelName(getTypeDeclaration(inner)) + "]";
    }
    else if (p instanceof MapProperty) {
      MapProperty mp = (MapProperty) p;
      Property inner = mp.getAdditionalProperties();
      return "Map.Map Text " + toModelName(getTypeDeclaration(inner));
    }
    return toModelName(super.getTypeDeclaration(p));
  }

  /**
   * Optional - swagger type conversion.  This is used to map swagger types in a `Property` into
   * either language specific types via `typeMapping` or into complex models if there is not a mapping.
   *
   * @return a string value of the type or complex model for this property
   * @see io.swagger.models.properties.Property
   */
  @Override
  public String getSwaggerType(Property p) {
    String swaggerType = super.getSwaggerType(p);
    String type = null;
    if(typeMapping.containsKey(swaggerType)) {
      type = typeMapping.get(swaggerType);
      if(languageSpecificPrimitives.contains(type))
        return toModelName(type);
    }
    else
      type = swaggerType;
    return toRelativeModelName(type);
  }

  @Override
  public Path toModelFilepath(String name) {
    return Paths.get(capitalizeModel(name).replace(".", File.separator));
  }

  @Override
  public String toModelFilename(String name) {
    return capitalizeModel(name);
  }

  @Override
  public String toModelPackage(String name) {
    String modelPackage = extractModelPackage(name);
    if (modelPackage.length() > 0) {
      return modelPackage() + "." + modelPackage;
    }
    return modelPackage();
  }

  @Override
  public String toModelName(final String name) {
    return initialCaps(modelNamePrefix + extractModelName(name) + modelNameSuffix);
  }

  @Override
  public String toCanonicalModelName(String name) {
    return toModelPackage(name) + "." + toModelName(name);
  }

  @Override
  public String toRelativeModelName(String name) {
    String modelPackage = extractModelPackage(name);
    if (modelPackage.length() > 0) {
      return extractModelPackage(name) + "." + toModelName(name);
    } else {
      return toModelName(name);
    }
  }

  private String extractModelPackage(String name) {
    int idx = name.lastIndexOf('.');
    String modelPackage = "";
    if (idx > 0 ) {
      modelPackage = capitalizeModel(name).substring(0, idx);
    }
    return modelPackage;
  }

  private String extractModelName(String name) {
    int idx = name.lastIndexOf('.');
    String result = name;
    if (idx > 0 ) {
      result = capitalizeModel(name).substring(idx + 1);
    }
    return result;
  }

  // capitalize package.name to Package.Name
  private String capitalizeModel(String name) {
    String[] parts = name.split("\\.");
    if (parts.length > 1) {
      return Arrays.stream(parts)
              .map(this::initialCaps)
              .collect(Collectors.joining("."));
    } else {
      return initialCaps(name);
    }
  }


  @Override
  public CodegenOperation fromOperation(String resourcePath, String httpMethod, Operation operation, Map<String, Model> definitions, Swagger swagger){
    CodegenOperation op = super.fromOperation(resourcePath, httpMethod, operation, definitions, swagger);
    RouteBuilder rb = new RouteBuilder(op).build();
    op.vendorExtensions.put("x-routeType", rb.path.stream().collect(Collectors.joining(" :> ")));
    op.vendorExtensions.put("x-clientType", rb.type.stream().collect(Collectors.joining(" -> ")));
    return op;
  }

  class RouteBuilder {
    CodegenOperation op;
    List<String> path = new ArrayList<>();
    List<String> type = new ArrayList<>();

    public RouteBuilder(CodegenOperation op) {
      this.op = op;
    }

    public RouteBuilder build() {
      path = pathToServantRoute(op.path, op.pathParams);
      type = pathToClientType(op.path, op.pathParams);

      // Query parameters appended to routes
      for (CodegenParameter param : op.queryParams) {
        String paramType = param.dataType;
        if(param.isListContainer != null && param.isListContainer) {
          paramType = makeQueryListType(paramType, param.collectionFormat);
        }
        path.add("Servant.QueryParam \"" + param.baseName + "\" " + paramType);
        type.add("Maybe " + param.dataType);
      }

      // Either body or form data parameters appended to route
      // As far as I know, you cannot have two ReqBody routes.
      // Is it possible to have body params AND have form params?
      String bodyType = null;
      if (op.getHasBodyParam()) {
        for (CodegenParameter param : op.bodyParams) {
          path.add("Servant.ReqBody '[Servant.JSON] " + toModelName(param.dataType));
          bodyType = toModelName(param.dataType);
        }
      } else if(op.getHasFormParams()) {
        // Use the FormX data type, where X is the conglomerate of all things being passed
        String formName = "Servant.Form" + camelize(op.operationId);
        bodyType = formName;
        path.add("Servant.ReqBody '[Servant.FormUrlEncoded] " + formName);
      }
      if(bodyType != null) {
        type.add(bodyType);
      }

      // Special headers appended to route
      for (CodegenParameter param : op.headerParams) {
        path.add("Servant.Header \"" + param.baseName + "\" " + param.dataType);

        String paramType = param.dataType;
        if(param.isListContainer != null && param.isListContainer) {
          paramType = makeQueryListType(paramType, param.collectionFormat);
        }
        type.add("Maybe " + paramType);
      }

      supplementalHeader.ifPresent(header -> {
        path.add("Servant.Header \"" + header + "\" Text");
        type.add("Maybe Text"); // FIXME
      });

      // Add the HTTP method and return type
      String returnType = op.returnType;
      if (returnType == null || returnType.equals("null")) {
        returnType = "()";
      }
      if (returnType.indexOf(" ") >= 0) {
        returnType = "(" + returnType + ")";
      }
      path.add("Servant.Verb 'Servant." + op.httpMethod.toUpperCase() + " 200 '[Servant.JSON] " + returnType);
      type.add("Manager");
      type.add("BaseUrl");
      type.add("ExceptT ServantError IO " + returnType);
      return this;
    }


    // Convert an HTTP path to a Servant route, including captured parameters.
    // For example, the path /api/jobs/info/{id}/last would become:
    //      "api" :> "jobs" :> "info" :> Capture "id" IdType :> "last"
    // IdType is provided by the capture params.
    private List<String> pathToServantRoute(String path, List<CodegenParameter> pathParams) {
      // Map the capture params by their names.
      HashMap<String, String> captureTypes = new HashMap<String, String>();
      for (CodegenParameter param : pathParams) {
        captureTypes.put(param.baseName, param.dataType);
      }

      // Cut off the leading slash, if it is present.
      if (path.startsWith("/")) {
        path = path.substring(1);
      }

      // Convert the path into a list of servant route components.
      List<String> pathComponents = new ArrayList<String>();
      for (String piece : path.split("/")) {
        if (piece.startsWith("{") && piece.endsWith("}")) {
          String name = piece.substring(1, piece.length() - 1);
          pathComponents.add("Servant.Capture \"" + name + "\" " + captureTypes.get(name));
        } else {
          pathComponents.add("\"" + piece + "\"");
        }
      }

      // Intersperse the servant route pieces with :> to construct the final API type
      return pathComponents;
    }

    // Extract the arguments that are passed in the route path parameters
    private List<String> pathToClientType(String path, List<CodegenParameter> pathParams) {
      // Map the capture params by their names.
      HashMap<String, String> captureTypes = new HashMap<String, String>();
      for (CodegenParameter param : pathParams) {
        captureTypes.put(param.baseName, param.dataType);
      }

      // Cut off the leading slash, if it is present.
      if (path.startsWith("/")) {
        path = path.substring(1);
      }

      // Convert the path into a list of servant route components.
      List<String> type = new ArrayList<String>();
      for (String piece : path.split("/")) {
        if (piece.startsWith("{") && piece.endsWith("}")) {
          String name = piece.substring(1, piece.length() - 1);
          type.add(captureTypes.get(name));
        }
      }

      return type;
    }

    private String makeQueryListType(String type, String collectionFormat) {
      type = type.substring(1, type.length() - 1);
      switch(collectionFormat) {
        case "csv": return "(Servant.QueryList 'CommaSeparated (" + type + "))";
        case "tsv": return "(Servant.QueryList 'TabSeparated (" + type + "))";
        case "ssv": return "(Servant.QueryList 'SpaceSeparated (" + type + "))";
        case "pipes": return "(Servant.QueryList 'PipeSeparated (" + type + "))";
        case "multi": return "(Servant.QueryList 'MultiParamArray (" + type + "))";
        default:
          throw new NotImplementedException();
      }
    }
  }

}
