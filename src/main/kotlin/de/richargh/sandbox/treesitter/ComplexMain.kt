package de.richargh.sandbox.treesitter

import org.treesitter.TSLanguage
import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TreeSitterJava


private const val javaCode = """
    import de.richargh.Bla;
    import de.richargh.Blubb;
    
    public class FooService {
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
    println(fileContext)
}

private fun traverseNode(node: TSNode, context: Context) {
    var childrenToExplore = 0 until node.childCount
    var currentContext = context
    when (node.type) {
        "import_declaration" -> {
            currentContext.addImport(node)
            childrenToExplore = 0..0
        }

        "class_declaration" -> {
            val (nextContext, classBodyIndex) = handleClassHeader(node, context)
            currentContext = nextContext
            childrenToExplore = classBodyIndex until node.childCount
        }
        "class_body" -> {

        }
    }

    childrenToExplore.forEach { index ->
        traverseNode(node.getChild(index), currentContext)
    }
}

private fun handleClassHeader(node: TSNode, context: Context): Pair<Context, Int> {
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

    fun builder(): ContextBuilder
}

abstract class BaseContext(override val previous: Context?, override val codeLines: List<String>) : Context {
    abstract val kind: String
    abstract val details: String
    private val children: MutableList<Context> = mutableListOf()
    private val imports: MutableList<String> = mutableListOf()

    override fun addContext(context: Context): Context {
        children.add(context)
        return context
    }

    override fun addImport(node: TSNode) {
        val importPath = contents(node, codeLines)
        imports.add(importPath)
    }

    override fun toString(): String {
        val indent = "  "
        return buildString {
            append(kind)
            append(" ")
            appendLine(details)
            if (imports.isNotEmpty()) {
                append(indent)
                appendLine(imports.joinToString("\n$indent"))
            }
            append(indent)
            appendLine(children.joinToString("\n$indent"))
        }
    }

    override fun builder(): ContextBuilder {
        return BaseContextBuilder(this, codeLines)
    }
}


interface ContextBuilder {
    fun buildPackageContext(): PackageContext
    fun buildClassContext(modifier: String, identifier: String): ClassContext
    fun buildFunctionContext(): FunctionContext
}

class BaseContextBuilder(private val previous: Context, private val codeLines: List<String>) : ContextBuilder {
    override fun buildPackageContext() = PackageContext(
        previous, codeLines
    )

    override fun buildClassContext(modifier: String, identifier: String) = ClassContext(
        modifier, identifier, previous, codeLines
    )

    override fun buildFunctionContext() = FunctionContext(
        previous, codeLines
    )
}

class FileContext(codeLines: List<String>) : BaseContext(null, codeLines) {
    override val kind: String = "File"
    override val details: String = ""
}

class PackageContext(previous: Context, codeLines: List<String>) : BaseContext(previous, codeLines) {
    override val kind: String = "Package"
    override val details: String = ""
}

class ClassContext(val modifier: String, val identifier: String, previous: Context, codeLines: List<String>) :
    BaseContext(previous, codeLines) {
    override val kind: String = "Class"
    override val details: String = "$identifier $modifier "
}

class FunctionContext(previous: Context, codeLines: List<String>) : BaseContext(previous, codeLines) {
    override val kind: String = "Function"
    override val details: String = ""
}