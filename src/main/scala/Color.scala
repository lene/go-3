package go3d

enum Color(val ascii: Char):
   override def toString: String = ascii.toString
   def unary_! = {
      ascii match
         case '@' => Color.White
         case 'O' => Color.Black
         case _ => throw IllegalArgumentException(toString)
   }
   case Empty extends Color(' ')
   case Black extends Color('@')
   case White extends Color('O')
   case Sentinel extends Color('·')
end Color