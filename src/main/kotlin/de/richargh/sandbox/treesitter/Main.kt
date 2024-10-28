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
    println("-------------------------")
    println(importStatement)
}
