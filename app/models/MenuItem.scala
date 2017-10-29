package models

case class MenuItem(id: Long,
                    name: String,
                    price: String,
                    ingredients: List[String] = Nil,
                    images: List[String] = Nil)
