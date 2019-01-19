package scalaz.nio

import scala.annotation.{ StaticAnnotation, compileTimeOnly }
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

object BufferOpsMacro {

  val ERROR_MESSAGE = "The annotation can only be used with classes extending scalaz.nio.Buffer."

  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    import c.universe._

    def extractBufferClass(
      bufferClass: ClassDef
    ): (TypeName, Tree, Tree, List[ValDef], List[Tree], List[Tree]) = bufferClass match {
      case q"class $className private (private[nio] val javaBuffer: $b, ..$params) extends Buffer[$a, $b2](javaBuffer) with ..$parents { ..$body }"
          if b.equalsStructure(b2) =>
        (className, a, b, params, parents, body)
      case _ => c.abort(c.enclosingPosition, ERROR_MESSAGE)
    }

    def extractBufferObject(bufferObject: ModuleDef): (TermName, List[Tree], List[Tree]) =
      bufferObject match {
        case q"object $objectName extends ..$parents { ..$body }" =>
          (objectName, parents, body)
      }

    def updatedBufferClass(
      className: TypeName,
      a: Tree,
      b: Tree,
      params: List[ValDef],
      parents: List[Tree],
      body: List[Tree]
    ): Tree =
      q"""
        class $className private (private[nio] val javaBuffer: $b, ..$params) extends Buffer[$a, $b](javaBuffer) with ..$parents {
          ..$body

          type Self = $className

          def array: IO[Exception, Array[$a]] = IO.syncException(javaBuffer.array())

          def order: IO[Nothing, ByteOrder] = IO.now(javaBuffer.order())

          def slice: IO[Exception, $className] = IO.syncException(javaBuffer.slice()).map(new $className(_))

          def get: IO[Exception, $a] = IO.syncException(javaBuffer.get())

          def get(i: Int): IO[Exception, $a] = IO.syncException(javaBuffer.get(i))

          def put(element: $a): IO[Exception, $className] = IO.syncException(javaBuffer.put(element)).map(new $className(_))

          def put(index: Int, element: $a): IO[Exception, $className] = IO.syncException(javaBuffer.put(index, element)).map(new $className(_))

          def asReadOnlyBuffer: IO[Exception, $className] = IO.syncException(javaBuffer.asReadOnlyBuffer()).map(new $className(_))
        }
      """

    def updatedBufferObject(
      className: TypeName,
      a: Tree,
      b: Tree,
      objectName: TermName,
      parents: List[Tree],
      body: List[Tree]
    ): Tree = {

      val bufferStatic = c.typecheck(q"(??? : $b)").tpe.companion
      val allocate     = bufferStatic.decl(TermName("allocate"))
      val wrap         = bufferStatic.decl(TermName("wrap"))

      q"""
        object $objectName extends ..$parents {
          ..$body

          private[nio] def apply(javaBuffer: $b): $className = new $className(javaBuffer)

          def apply(capacity: Int): IO[Exception, $className] =
            IO.syncException($allocate(capacity)).map(new $className(_))

          def apply(array: Array[$a]): IO[Exception, $className] =
            IO.syncException($wrap(array)).map(new $className(_))

          def apply(array: Array[$a], offset: Int, length: Int): IO[Exception, $className] =
            IO.syncException($wrap(array, offset, length)).map(new $className(_))
        }
      """
    }

    val (
      (className, a, b, params, classParents, classBody),
      (objectName, objectParents, objectBody)
    ) = annottees.map(_.tree) match {
      case (bufferClass: ClassDef) :: (bufferObject: ModuleDef) :: Nil
          if bufferClass.name.toTermName == bufferObject.name =>
        (extractBufferClass(bufferClass), extractBufferObject(bufferObject))
      case (bufferClass: ClassDef) :: Nil =>
        val (bufferClassTerms @ (classTypeName, _, _, _, _, _)) = extractBufferClass(bufferClass)
        val bufferObjectTerms                                   = (classTypeName.toTermName, Nil, Nil)
        (bufferClassTerms, bufferObjectTerms)
      case _ => c.abort(c.enclosingPosition, ERROR_MESSAGE)
    }

    c.Expr(q"""
        ${updatedBufferClass(className, a, b, params, classParents, classBody)}

        ${updatedBufferObject(className, a, b, objectName, objectParents, objectBody)}
      """)
  }
}

@compileTimeOnly("enable macro paradise to expand macro annotations")
class BufferOps(valueType: String, bufferType: String) extends StaticAnnotation {

  def macroTransform(annottees: Any*) = macro BufferOpsMacro.impl
}
