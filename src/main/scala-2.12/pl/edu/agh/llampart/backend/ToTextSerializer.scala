package pl.edu.agh.llampart.backend

trait ToTextSerializer[T] {
  def serialize(elem: T): String
}

object ToTextSerializer {

  //implement
  implicit class SerializeExtension[T](private val elem: T) extends AnyVal {
    def serialize(implicit ev: ToTextSerializer[T]): String = ev.serialize(elem)
  }

}
