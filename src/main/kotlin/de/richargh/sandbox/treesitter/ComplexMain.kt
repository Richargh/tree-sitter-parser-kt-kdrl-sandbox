package de.richargh.sandbox.treesitter

import org.treesitter.TSLanguage
import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TreeSitterJava


private const val javaCode = """
    import de.richargh.Bla;
    import de.richargh.Blubb;
    
    public class FooService {
        private final String name = Blubb.name();
        private final Outside outside;
        public FooService(){
            this.outside = new Outside();
        }
        
        public int act(){
            this.outside.noop();
            return 42;
        }
    }
    
    class Outside {
        public void noop(){
            // do nothing
        }
    }
    """

fun main() {
    val javaCodeLines = javaCode.lines()
    val parser = TSParser()
    val java: TSLanguage = TreeSitterJava()

    parser.setLanguage(java)
    val tree = parser.parseString(null, javaCode)
    val rootNode = tree.rootNode
    val importNode = rootNode.getChild(0)
    val importStatement = contents(importNode, javaCodeLines)
    println(tree.rootNode)
    println(tree.rootNode.getChild(0))
    println(tree.rootNode.getChild(0).type)
    println(importStatement)

    printNode(rootNode, "")
    val fileContext = FileContext(javaCodeLines)
    traverseNode(rootNode, fileContext)
    println(fileContext.format(0))
}

private fun traverseNode(node: TSNode, context: Context) {
    var childrenToExplore = 0 until node.childCount
    var currentContext = context
    when (node.type) {
        "import_declaration" -> {
            currentContext.addImport(node)
            childrenToExplore = IntRange.EMPTY
        }

        "class_declaration" -> {
            val (nextContext, classBodyIndex) = handleClassDeclaration(node, context)
            currentContext = nextContext
            childrenToExplore = classBodyIndex until node.childCount
        }

        "field_declaration" -> {
            handleFieldDeclaration(node, context)
            childrenToExplore = IntRange.EMPTY
        }

        "method_invocation" -> {
            handleMethodInvocation(node, context)
        }

        "method_declaration" -> {
            handleMethodDeclaration(node, context)
        }
    }

    childrenToExplore.forEach { index ->
        traverseNode(node.getChild(index), currentContext)
    }
}

private fun handleClassDeclaration(node: TSNode, context: Context): Pair<Context, Int> {
    var bodyIndex = 0
    var modifier = "default"
    var identifier = "none"
    (0 until node.childCount).forEach { index ->
        val currentNode = node.getChild(index)
        when (currentNode.type) {
            "modifiers" -> modifier = contents(currentNode, context.codeLines)
            "identifier" -> identifier = contents(currentNode, context.codeLines)
            "class_body" -> bodyIndex = index
        }
    }

    return Pair(
        context.addContext(context.builder().buildClassContext(modifier, identifier)),
        bodyIndex
    )
}

private fun handleFieldDeclaration(node: TSNode, context: Context) {
    var modifier = "default"
    var typeIdentifier = "none"
    var identifier = "none"
    (0 until node.childCount).forEach { index ->
        val currentNode = node.getChild(index)
        when (currentNode.type) {
            "modifiers" -> modifier = contents(currentNode, context.codeLines)
            "type_identifier" -> typeIdentifier = contents(currentNode, context.codeLines)
            "variable_declarator" -> identifier = contents(currentNode, context.codeLines)
        }
    }

    context.addField(modifier, identifier, typeIdentifier)
}

private fun handleMethodInvocation(node: TSNode, context: Context) {
    var fieldAccess = ""
    var identifier = ""
    var argumentList = ""
    (0 until node.childCount).forEach { index ->
        val currentNode = node.getChild(index)
        when (currentNode.type) {
            "field_access" -> fieldAccess = contents(currentNode, context.codeLines)
            "identifier" -> identifier += contents(currentNode, context.codeLines)
            "argument_list" -> argumentList = contents(currentNode, context.codeLines)
        }
    }

    context.addMethodInvocation(fieldAccess, identifier, argumentList)
}

private fun handleMethodDeclaration(node: TSNode, context: Context): Context {
    var modifiers = ""
    var returnType = ""
    var identifier = ""
    var parameters = ""
    (0 until node.childCount).forEach { index ->
        val currentNode = node.getChild(index)
        when (currentNode.type) {
            "modifiers" -> modifiers = contents(currentNode, context.codeLines)
            "void_type" -> returnType = contents(currentNode, context.codeLines)
            "identifier" -> identifier = contents(currentNode, context.codeLines)
            "formal_parameters" -> parameters = contents(currentNode, context.codeLines)
        }
    }

    return context.addContext(context.builder().buildFunctionContext(modifiers, identifier, parameters, returnType))
}

private fun printNode(node: TSNode, indent: String) {
    println("$indent${node.type} [${node.startPoint.row}, ${node.startPoint.column}] - [${node.endPoint.row}, ${node.endPoint.column}]")

    (0 until node.childCount).forEach { index ->
        printNode(node.getChild(index), "  $indent")
    }
}


interface Context {
    val previous: Context?
    val codeLines: List<String>
    fun addContext(context: Context): Context
    fun addImport(node: TSNode)
    fun addField(modifier: String, identifier: String, typeIdentifier: String)
    fun addMethodInvocation(fieldAccess: String, identifier: String, argumentList: String)

    fun format(indent: Int): String

    fun builder(): ContextBuilder
}

abstract class BaseContext(override val previous: Context?, override val codeLines: List<String>) : Context {
    private val children: MutableList<Context> = mutableListOf()
    private val imports: MutableList<String> = mutableListOf()
    private val fields: MutableList<String> = mutableListOf()
    private val invokedMethods: MutableList<String> = mutableListOf()

    override fun addContext(context: Context): Context {
        children.add(context)
        return context
    }

    override fun addImport(node: TSNode) {
        val importPath = contents(node, codeLines)
        imports.add(importPath)
    }

    override fun addField(modifier: String, identifier: String, typeIdentifier: String) {
        fields.add("$modifier $identifier: $typeIdentifier")
    }

    override fun addMethodInvocation(fieldAccess: String, identifier: String, argumentList: String) {
        invokedMethods.add("$fieldAccess.$identifier$argumentList")
    }

    abstract fun formatHeader(): String

    override fun format(indent: Int): String {
        val headerIndent = (0 until indent).joinToString(separator = "") { " " }
        val subIndent = (0 until indent + 2).joinToString(separator = "") { " " }
        return buildString {
            append(headerIndent)
            append(formatHeader())

            if (imports.isNotEmpty()) {
                append(subIndent)
                appendLine(imports.joinToString("\n$subIndent"))
            }

            if (fields.isNotEmpty()) {
                append(subIndent)
                appendLine(fields.joinToString("\n$subIndent"))
            }

            if (invokedMethods.isNotEmpty()) {
                append(subIndent)
                append("Invoke: ")
                appendLine(invokedMethods.joinToString("\n${subIndent}Invoke: "))
            }

            if (children.isNotEmpty()) {
                append(subIndent)
                appendLine(children.joinToString(separator = "") { it.format(indent + 2) })
            }
        }
    }

    override fun builder(): ContextBuilder {
        return BaseContextBuilder(this, codeLines)
    }
}


interface ContextBuilder {
    fun buildPackageContext(): PackageContext
    fun buildClassContext(modifier: String, identifier: String): ClassContext
    fun buildFunctionContext(modifiers: String, identifier: String, parameters: String, returnType: String): FunctionContext
}

class BaseContextBuilder(private val previous: Context, private val codeLines: List<String>) : ContextBuilder {
    override fun buildPackageContext() = PackageContext(
        previous, codeLines
    )

    override fun buildClassContext(modifier: String, identifier: String) = ClassContext(
        modifier, identifier, previous, codeLines
    )

    override fun buildFunctionContext(modifiers: String, identifier: String, parameters: String, returnType: String) =
        FunctionContext(
            modifiers, identifier, parameters, returnType, previous, codeLines
        )
}

class FileContext(codeLines: List<String>) : BaseContext(null, codeLines) {
    override fun formatHeader(): String {
        return buildString {
            appendLine("File")
        }
    }
}

class PackageContext(previous: Context, codeLines: List<String>) : BaseContext(previous, codeLines) {
    override fun formatHeader(): String {
        return buildString {
            appendLine("Package")
        }
    }
}

class ClassContext(
    val modifier: String, val identifier: String,
    previous: Context, codeLines: List<String>
) :
    BaseContext(previous, codeLines) {
    override fun formatHeader(): String {
        return buildString {
            appendLine("$modifier class $identifier ")
        }
    }
}

class FunctionContext(
    val modifiers: String, val identifier: String, val parameters: String, val returnType: String,
    previous: Context, codeLines: List<String>
) : BaseContext(previous, codeLines) {
    override fun formatHeader(): String {
        return buildString {
            appendLine("$modifiers $identifier $parameters: $returnType")
        }
    }
}