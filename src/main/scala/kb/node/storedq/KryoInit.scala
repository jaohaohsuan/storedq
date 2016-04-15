package kb.node.storedq

import com.esotericsoftware.kryo.Kryo

/**
  * Created by henry on 4/11/16.
  */
class KryoInit {
  def customize(kryo: Kryo): Unit  = {
    kryo.register(classOf[domain.StoredQuery])
    kryo.register(classOf[domain.NamedBoolClause])
    kryo.register(classOf[domain.MatchBoolClause])
    kryo.register(classOf[domain.ItemCreated])
    kryo.register(classOf[domain.ItemsChanged])
  }
}
