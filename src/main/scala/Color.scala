package go3d

enum Color(val ascii: Char):
   override def toString: String = ascii.toString
   case Empty extends Color(' ')
   case Black extends Color('@')
   case White extends Color('O')
   case Sentinel extends Color('·')
   case Red extends Color('R')
   case Green extends Color('G')
   case Blue extends Color('B')
   case Undefined extends Color('?')
end Color