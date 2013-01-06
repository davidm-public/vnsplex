package model.graph;

/**
 * The Key class can be used as a hash key provider.
 */
public final class Key {

	//unique id object
    private final Long id;

    public Key(Long id) {
      if (id == null) {
        throw new IllegalArgumentException();
      }
      this.id = id;
    }

    public int hashCode() {
      return id.hashCode();
    }

    public Long getId() {
        return id;
      }
    
    public boolean equals(Object obj) {
      if (!(obj instanceof Key)) {
    	  return false;
      }
      return id.equals( ((Key) obj).id );
    }

}

