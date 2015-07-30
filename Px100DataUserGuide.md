# Px100 Data Framework User Guide
## Minimal Configuration: In-Memory Grid, No On-Disk Persistence
The example below uses Ignite:

```xml
    <beans xmlns="http://www.springframework.org/schema/beans"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xmlns:util="http://www.springframework.org/schema/util"
           xsi:schemaLocation="
            http://www.springframework.org/schema/beans
            http://www.springframework.org/schema/beans/spring-beans.xsd
            http://www.springframework.org/schema/util
            http://www.springframework.org/schema/util/spring-util.xsd">
        <bean id="igniteConfig" class="org.apache.ignite.configuration.IgniteConfiguration">
            <property name="gridName" value="myDataGrid"/>
    
            <!-- Optional arbitrary "tags" to enumerate nodes, group them into cluster groups, etc. -->
            <property name="userAttributes">
                <map>
                    <entry key="type" value="dev"/>
                </map>
            </property>
    
            <property name="marshaller">
                <bean class="org.apache.ignite.marshaller.optimized.OptimizedMarshaller">
                    <property name="requireSerializable" value="false"/>
                </bean>
            </property>
    
            <property name="includeEventTypes">
                <list>
                    <util:constant static-field="org.apache.ignite.events.EventType.EVT_NODE_JOINED"/>
                    <!--util:constant static-field="org.apache.ignite.events.EventType.EVT_CACHE_OBJECT_PUT"/-->
                    <!--util:constant static-field="org.apache.ignite.events.EventType.EVT_CACHE_OBJECT_READ"/-->
                </list>
            </property>
    
            <property name="atomicConfiguration">
                <bean class="org.apache.ignite.configuration.AtomicConfiguration">
                    <property name="backups" value="1"/>
                    <property name="cacheMode" value="PARTITIONED"/>
                    <!-- Not using native ID generators (aka "sequences") anyway:  property name="atomicSequenceReserveSize" value="1000"/-->
                </bean>
            </property>
    
            <property name="gridLogger">
                <bean class="org.apache.ignite.logger.log4j.Log4JLogger"/>
            </property>
    
            <!--property name="publicThreadPoolSize" value="15"/-->
            <!--property name="managementThreadPoolSize" value="10"/-->
            <!--property name="systemThreadPoolSize" value="10"/-->
            <!--property name="utilityCachePoolSize" value="10"/-->
            <!--property name="marshallerCachePoolSize" value="10"/-->
    
            <property name="metricsLogFrequency" value="0"/>
            <property name="metricsUpdateFrequency" value="15000"/>
    
            <property name="peerClassLoadingEnabled" value="false"/>
            <!--property name="peerClassLoadingThreadPoolSize" value="10"/-->
    
            <property name="transactionConfiguration">
                <bean class="org.apache.ignite.configuration.TransactionConfiguration">
                    <property name="defaultTxConcurrency" value="PESSIMISTIC"/>
                    <property name="defaultTxIsolation" value="READ_COMMITTED"/>
                    <property name="defaultTxTimeout" value="30000"/>
                </bean>
            </property>
    
            <!-- Uncomment to use static IP discovery (instead of default multicast: see TcpDiscoveryMulticastIpFinder) -->
            <!--property name="discoverySpi">
                <bean class="org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi">
                    <property name="ipFinder">
                        <bean class="org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder">
                            <property name="addresses">
                                <list>
                                    <value>127.0.0.1:47500..47509</value>
                                </list>
                            </property>
                        </bean>
                    </property>
                </bean>
            </property-->
    
            <!-- Uncomment to use AWS S3 based discovery (instead of default multicast: see TcpDiscoveryMulticastIpFinder) -->
            <!--property name="discoverySpi">
                <bean class="org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi">
                    <property name="ipFinder">
                        <bean class="org.apache.ignite.spi.discovery.tcp.ipfinder.s3.TcpDiscoveryS3IpFinder">
                            <property name="awsCredentials">
                                <bean class="com.amazonaws.auth.BasicAWSCredentials">
                                    <constructor-arg value="xxx_ACCESS_KEY_ID" />
                                    <constructor-arg value="xxx_SECRET_ACCESS_KEY" />
                                </bean>
                            </property>
                            <property name="bucketName" value="xxx_BUCKET_NAME"/>
                        </bean>
                    </property>
                </bean>
            </property-->
        </bean>
        
        <bean id="storageProvider" class="com.px100systems.data.plugin.storage.ignite.IgniteInMemoryStorage">
            <property name="config" ref="igniteConfig"/>
        </bean>
    
        <bean id="runtimeStorage" class="com.px100systems.data.core.RuntimeStorage">
            <property name="provider" ref="storageProvider"/>
        </bean>
        
        <bean id="dataStorage" class="com.px100systems.data.core.InMemoryDatabase">
            <property name="runtimeStorage" ref="runtimeStorage"/>
    
            <property name="persistence" value="None"/>
    
            <property name="baseEntityPackages">
                <list>
                    <value>x.y.z</value>
                </list>
            </property>
            <property name="backupDirectory" value="."/>
        </bean>
    </beans>
```

For XML haters, you can surely use Spring Java configuration or even call all those constructors and setters manually. 
Though configuration like that belongs to external XML files in the configured "additional classpath" outside of the deployment unit 
(.jar or .war). 
  
Your "main" class (or controller, etc.) should have DatabaseStorage autowired and that's it.

```java
import x.y.z.a.b.c.MyEntity;
... 
 
@Component 
public class SomeClass {   
    ...
    @Autowired // or @Resource(name="dataStorage") if you prefer injecting it that way
    private DatabaseStorage ds;
    ...
    
    public void hello(Long id) {
        Transaction tx = ds.transaction(0);
        MyEntity bean = tx.get(MyEntity.class, id);
        bean.setSomeStringProperty("Hello, Px100 Data!");
        tx.update(bean, false);
        tx.commit();
    }
    
    ...
}    
```

**Note:** Px100 Data uses "pseudo-transactions" for performance reasons: minimal locking. It means that no locking occurs when we call
ds.transaction(). If all we did, were get operations, we wouldn't even need to commit - however both read and write API is provided through
Transaction only for consistency. Telling Transaction to update() simply puts the bean on the update list. Only when we call commit(), 
the actual "real" transaction happens (simulated in Mongo anyway), as the framework goes through its lists of accumulated inserts, updates, 
and deletes, locking the data units (Mongo collections, Hazelcast maps, or Ignite caches) and actually writing the data to them.

It also means you can (but shouldn't) have some complex long-running logic between ds.transaction() and tx.commit(), as the only "real" 
transaction happens inside commit(). And if you never call commit() - don't put it inside the finally block - e.g. when some exception occurs,
the pseudo-transaction is naturally rolled backed because nothing was even attempted to be written anyway: it did not reach that point.
 
This approach represents traditional pessimistic locking with READ_COMMITTED isolation. A well-thought system written from scratch (or refactored) 
doesn't need anything more.
  
As far, as transaction "propagation", you can pass Transaction instance anywhere you want like I/O streams - making sure that you 
commit it where you initially created it. You wouldn't close a stream you received from somewhere else, would you? Higher-level Px100 Platform 
takes care of transactions automatically. In a custom system of yours it is advised to create and commit them at the highest server-side: controller 
level.
    
Once committed, Transaction will not accept any operations, even read ones.    
    
Sometimes you need to break a long transaction into "batches". It can be done like this:

```java
        Transaction tx = ds.transaction(0);

        for (int i = 0, n = BATCH_SIZE; i < n; i++)
            tx.insert(beans[i]);
        tx.commit();
        tx = tx.transaction(0);
        
        // Do it again... and again... and again
```
     
If you are wondering what zero means in transaction() calls, it is default tenant ID. Px100 Platform is intended primarily for multi-tenant SaaS
applications, so using it w/o tenants is rare. So we didn't include helper methods w/o parameters. You'll manage to understand the 
multi-tenancy concept. We skipped tenant config right now for simplicity and will explain it later. For now just remember to use zero 
(in ne method call anyway) in a single-tenant app.     
     
## Entities
### NoSQL Basics
If you have used NoSQL databases like Mongo, you can skip this section.

A NoSQL record aka "entity" is the indexed piece of data saved to the underlying storage. Most key/value NoSQL databases including Hazelcast
call them "map entries" with exactly the same semantics as java.util.Map. Ignite calls its maps "caches" because it is big on JCache (JSR107)
compliance. Mongo uses the term "document" (mapped to some "id" like any map entry), as it logically stores its data as JSON documents.  

Either way, entities have very little in common with flat relational database records. Entities are big and coarse-grained because they are
self-contained. How much they should be self-contained depends. Here's the example we will use in this guide:
 
### Entity Class Hierarchy
**Base Entity** you can put all your serialization code in e.g. Externalizable - preferred by Ignite. 
Write this class once and derive all concrete entities from it.
```java
package p.q.r; // doesn't matter - doesn't need to be scanned

...
 
public abstract class BaseEntity extends Entity implements Externalizable {
	private transient ExecutionContext context;
	...

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerializationDefinition.get(getClass()).write(out, this);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		SerializationDefinition.get(getClass()).read(in, this);
	}

	// getters and setters
	...

	// methods
	...
}
```
<br/>
Here's Portable example for Hazelcast using our internal Portable implementation also based on SerializationDefinition.
```java
public abstract class BaseEntity extends Entity implements Portable {
	...

	public BaseEntity() {
	}

	@Override
	public void writePortable(PortableWriter writer) throws IOException {
		PortableImpl.writePortable(this, writer);
	}

	@Override
	public void readPortable(PortableReader reader) throws IOException {
		PortableImpl.readPortable(this, reader);
	}

	...
}
```
 
**Note:** We decided not to support standard java.util.Serializable. Believe you or not it requires the developer to write more code: 
add Serializable to all superclasses, sub-objects, and collection members, etc. instead of implementing Externalizable or Portable in one base class.
The main reason we abandoned Serializable is because it practically kills data grid performance and memory footprint, expecially Hazelcast's. 
Our efficient reflection-based serialization comes for free. Use it it write something better. Since our base entity classes: StoredBean and Entity
don't implement Serializable, you cannot use it in any subclasses of those.

*Mongo doesn't require explicit serialization.*

Here's the entity hierarchy:
<pre>
    StoredBean: declares id (Long) - assigned by Transaction.insert()
        ^
        |
      Entity: adds tenantId (Integer), createdAt (Date), and modifiedAt (Date) - assigned by Transaction.insert() and update()
        ^
        |
    Your BaseEntity (see above): implements serialization
        ^
        |
    Your concrete entity classes (see below): encapsulate data (state) and methods (OOP behavior)
        ^
        |
       ... 
</pre>

### Concrete Domain-Specific Entity Classes
**Main Entity**
```java
package x.y.z.a.b.c; // matters - should be under the scanned package x.y.z specified in the DataStorage XML configuration

...

public class MedicalInsuranceApplication extends BaseEntity {
	private String name;

	@SerializedCollection(type = String.class)
	private List<String> serializedSsn;

	private Date dob;
	
	@SerializedCollection(type = Dependent.class)
	private List<Dependent> dependents;
	
	private Details details;

	@Index
	@SerializedGetter
	public Integer getAge() {
		// calculate and return age
		...
	}

	public List<Dependent> getDependents() {
		return dependents;
	}

	public void setDependents(List<Dependent> dependents) {
		this.dependents = dependents;
	}

	@Index
	public Date getDob() {
		return dob;
	}

	public void setDob(Date dob) {
		this.dob = dob;
	}

	@QueryField
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getSerializedSsn() {
		return serializedSsn;
	}

	public void setSerializedSsn(List<String> serializedSsn) {
		this.serializedSsn = serializedSsn;
	}

	public String[] getSsn() {
		return serializedSsn == null ? null : serializedSsn.toArray(new String[serializedSsn.size()]);
	}

	public void setSsn(String[] ssn) {
		serializedSsn = ssn == null ? new ArrayList<>() : Arrays.asList(ssn);
	}

	public Details getDetails() {
		return details;
	}

	public void setDetails(Details details) {
		this.details = details;
	}
	
	// methods
    ...
}
```
<br/>
Non-entity **sub-objects** and **collection members**:
```java
package x.y.something_else; // matters - should NOT be under the scanned package x.y.z specified in the DataStorage XML configuration

...

public class Dependent {
	private String name;
	private List<String> serializedSsn;
	private Date dob;
	private String relationship;

	// getters and setters
	...

	// methods
	...
}
```
```java
package x.y.something_else_entirely; // matters - should NOT be under the scanned package x.y.z specified in the DataStorage XML configuration

public class Details {
	...
}
```
<br/>
**Related Entity**
```java
package x.y.z.a.b.c; // matters - should be under the scanned package x.y.z specified in the DataStorage XML configuration

...

public class MedicalInsuranceApplicationHistory extends BaseEntity {
	private Long applicationId; // "FK" to MedicalInsuranceApplication
	
	private Long userId;
	private String userName; // duplicated, since it doesn'y changes: to avoid "joins" to display it
	
	private String action; // updated, approved, rejected, etc.
	private String comments;

	// getters and setters
	...

	// methods
	...
}
```
<br/>

Unlike "related" database tables, NoSQL entities should strive to be self-contained. Our example show a medical insurance application that
has a (one to one) Details sub-object and (one to many) Dependent collection. The number of dependents (applicant's family members) is
rather small, so it can be contained (encapsulated) inside MedicalInsuranceApplication. However an application may have thousands of
audit records (MedicalInsuranceApplicationHistory), so those should be stored separately - outside of MedicalInsuranceApplication.
Good NoSQL model should not have many related entities anyway.
      
### Packages and Package Scan
As you probably noticed, we pay special attention to packages our entity classes are in. If you recall the example configuration, we specified 
the base package to scan for entities:
```xml
            <property name="baseEntityPackages">
                <list>
                    <value>x.y.z</value>
                </list>
            </property>
``` 
Our top-level entities: MedicalInsuranceApplication and MedicalInsuranceApplicationHistory should be either in x.y.z, or somewhere underneath it.
All sub-objects and collection members (which BTW can also extend StoredBean, Entity or BaseEntity - nothing against it) should NOT be in scanned packages,
otherwise the framework would automatically create indexed storage units for them: Hazelcast's maps, Mongo's collections, and Ignite's caches.
    
Sub-objects and collection members also should not be concerned about serialization, as the encapsulating entity takes care of that.

As far, as entity class inheritance it can be as complex/nested, as one needs.
    
## Fields
Entities can only have serialized (non-transient) fields of the following types

* String
* java.util.Date
* Integer
* Long
* Double
* Boolean
* sub-entity (aka "sub-object")
* List&lt;sub-entity&gt;
* Set&lt;sub-entity&gt;

Sub-entities can have fields of the same types included nested sub-objects and collections. If you need to have something else, provide
special getters and setters. Our example uses one for ssn as String[] (vs. List<String>).  

As far as non-serialized transient fields (with Java "transient" modifier), you can have anything you want.

You can also have complex artificial getters to calculate something or dig deep inside collections and sub-objects. We used one for age above.
Even though Mongo and Ignite support the "dot notation" (to refer to sub-object fields in queries) we decided to stick with top-level fields
for simplicity and consistency. Anything else can be implemented via the aforementioned getters, that can (and should) be indexed.

If you looked inside StoredBean or Entity, you probably also noticed special xyzSort getters - used by the higher-level Px100 Platform for 
sorting items in paginated lists displayed in the UI. This is generally the usage pattern for artificial getters.

## Field Annotations
Px100 Data has the following annotations:

@SerializedCollection - specified for entity Lists and Sets to tell the serialization framework what member class to expect. 
Our collections are homogenous - at the very least use some base class in them, and the framework will figure out the inheritance.
  
@Index - specified for getters of indexed fields including the aforementioned artificial getters.
Only simple data: String, Date, Integer, Long, Double, and Boolean can be indexed - not collections or sub-objects. 
Use artificial getters for that.<br/>
**Note:** Hazelcast doesn't handle indexed nulls with grace, so make sure to use some "null value" like an empty string, zero, or 
conveniently defined Criteria.NULL_DATE.
 
@SerializedGetter - specified for artificial getters the serialization/query framework should care about, as you can have tons of other 
getters it shouldn't. Annotating a normal getter of a serialized field will result in "double serialization" error.
  
@QueryField - this is Ignite-specific annotation for getters of normal serialized fields, that are not indexed (rare). 
@Index automatically makes some field a "query field". 
  
Strive for maximum index coverage, as NoSQL is nothing, but indexed BLOBs. But also understand the cost of indexing: slower writes.  

## Multi-Tenancy
Px100 Platform was created with a single goal: to automate multi-tenant SaaS application development. Multi-tenancy is built into Px100 data,
and we are not going to "turn it off". It is a very simple concept. We made it transparent/automatic for the developer, so anyone w/o
tenants will survive. The only place you'd see a tenant in that case is specifying zero as Transaction's tenantId. We are not going to hide it 
via some helper method that takes no parameters because you need to acknowledge that field's existence.
   
However if you, like us, build multi-tenant systems, you'd be pleased to find the multi-tenancy completely taken care of by the framework.
Entities are stored in "units" (Hazelcast's maps, Mongo's collections, and Ignite's caches). If you use Mongo and examine auto-created 
collections in Robomongo or another tool, you'll notice how they are named. E.g. our MedicalInsuranceApplication collection would be named
"MedicalInsuranceApplication___0", where zero is the tenant ID. Units, and subsequently queries are per-tenant, which is an important security 
measure, as it prevents queries from crossing tenant boundaries, and second it improves performance by implicitly partitioning (sharding) data
by tenant.
 
Tenants are loaded and managed via TenantManager (part of the bigger Px100 Platform, not Px100 Data), so you'd need to write your own for 
your unique multi-tenancy needs:
 
* loading their configuration (see BaseTenantConfig) from some non-database storage e.g. a JSON or property file.
* calling DatabaseStorage.onNewTenant() to create a set of units for all recognized entities, when a new tenant is added.   
 
If your application is single-tenant, all you need to remember is that your default tenant ID is zero - when you examine raw data in Mongo or 
data grids. 

### Ignite-Specific Features and Caveats
@QueryField - see above 
 
### Hazelcast-Specific Features and Caveats 
1. Implement Portable serialization as shown above in BaseEntity.
2. Hazelcast relies on Comparable for ORDER BY logic. The framework provides a universal reflection-based fallback, however you should
implement special orderByXyz methods for every xyz field (including artificial getter ones) you use in ordered Criteria queries. See Entity
for examples.  

### Mongo-Specific Features and Caveats 
None at all.

## Usage Examples
Px100 Data only API to read and write data is Transaction. You obtain it via DataStorage.transaction() as show below. 
```java
    ...
    @Autowired
    private DatabaseStorage ds;
    ...
    
    public void example() {
        Transaction tx = ds.transaction(0);

        // do something
        ... 
        
        tx.commit(); // optional - only if you update data, however it doesn't hurt to always call it (does nothing when it has no inserts, updates, or deletes)
    }
    
    ...
```

### Inserts
```java
        Transaction tx = ds.transaction(0);

        MedicalInsuranceApplication entity = new MedicalInsuranceApplication();
        entity.setName("John Doe");
        ...
        
        tx.insert(entity);
        Long applicationId = entity.getId();
        
        MedicalInsuranceApplicationHistory auditRecord = new MedicalInsuranceApplicationHistory();
        auditRecord.setApplicationId(applicationId);
        ... 
        tx.insert(auditRecord);
        
        tx.commit(); 
```

### Update
```java
        Transaction tx = ds.transaction(0);

        MedicalInsuranceApplication entity = tx.get(MedicalInsuranceApplication.class, 125L);
        entity.setName("Ivan Petroff");
        ...
        
        // Conventional update w/o optimnistic check (second arg)
        tx.update(entity, false);

        // In-place update w/o serialization via the Entry Processor (supported by in-memory providers only)
        tx.update(MedicalInsuranceApplication.class, 256L, bean -> bean.setName("Peter Thiedoroff"));
        
        tx.commit(); 
```

### Delete
```java
import static com.px100systems.data.core.Criteria.*;

...

        Transaction tx = ds.transaction(0);

        MedicalInsuranceApplication entity = tx.get(MedicalInsuranceApplication.class, 125L);
        tx.deleteWithDependents(entity);
        ...
        
        // Criteria-based mass-delete
        tx.delete(MedicalInsuranceApplication.class, lt("dob", new Date()));
        
        tx.commit(); 
```

### Queries and Searches
See Criteria for filtering conditions. Remember, that queries only support top-level fields (including artificial getters). 

```java
import static com.px100systems.data.core.Criteria.*;

...

        Transaction tx = ds.transaction(0);

        long applicationCount = tx.delete(MedicalInsuranceApplication.class, null);
        long filteredApplicationCount = tx.delete(MedicalInsuranceApplication.class, 
            and(lt("dob", new Date()), eq("name", "John Doe")));

        MedicalInsuranceApplication entity = tx.findOne(MedicalInsuranceApplication.class, 
            eq("name", "John Doe"));
        
        boolean exists = tx.exists(MedicalInsuranceApplication.class, 
            eq("name", "John Doe")); 

        // Limited search: 
        //   criteria can be null to get all records
        //   orderBy (third arg) is optional (nullable) too 
        List<MedicalInsuranceApplication> applications = tx.find(MedicalInsuranceApplication.class, 
            lt("dob", new Date()), 
            Arrays.asList("dob DESC", "name"),
            20);

        // Limitless search with iterable result list (note the use of artificial (calculated) field "age" and null orderBy)
        //   criteria can be null to get all records
        //   orderBy (third arg) is optional (nullable) too 
        EntityCursor<MedicalInsuranceApplication> cursor = tx.find(MedicalInsuranceApplication.class, 
           lt("age", 40),  
           null);
        try {
            for (Iterator<MedicalInsuranceApplication> i = cursor.iterator(); i.hasNext();)
                System.out.println(i.next());)
        } finally {
            cursor.close();
        }   
        
        // No updates - no need to commit  
```

## Using RuntimeStorage Independently from DatabaseStorage
DatabaseStorage encapsulates RuntimeStorage using it to manage transient runtime data and also as a main "database" in case of data grids.
You can also autowire RuntimeStorage and use it directly to read/write transient cluster-wide data like atomic Longs. 

Generally you can store anything (not just entities and specifically not them) and use String keys (instead of Long IDs) for non-persisted
transient data in the grid. You can use the same Criteria queries on that complex data, though it gets complicated, as you'd need to take care of 
serialization, and register indexes via explicitly creating a per-tenant transient unit. Generally you'd not need anything remotely close to that in your project, 
so we are not going to explain it here. If you really want to go out of your way to bypass DatabaseStorage and avoid working with entities, 
you'll surely figure it out from the source code.

All "transient" data is also partitioned by tenant, just like normal entities.

RuntimeStorage is also your API to data grid messaging system. 

```java
    ...
    @Autowired
    private DatabaseStorage ds;

    @Autowired
    private RuntimeStorage rs;
    ...
    
    public void example() {
        Long atomicLong = rs.getLong("MyAtomicLong", 0);
        
        Long incremented = rs.incrementLong("MyAtomicLong", 0);
        
        rs.saveTransient(String.class, 0, "MyString", "Hello");
        String hello = rs.getTransient(String.class, 0, "MyString"); 
    }
    
    ...
```
       
## Traditional Database (Mongo)
In order to use Mongo, you need to replace InMemoryDatabase at the end of the example configuration with TraditionalDatabase.
Traditional databases still need the RuntimeStorage (Hazelcast or Ignite) to send cluster-wide events and maintain cluster-wide internal state. 
```xml
    ...

	<bean id="databaseProvider" class="com.px100systems.data.plugin.storage.mongo.MongoDatabaseStorage">
		<property name="connectionUrl" value="mongodb://user:password@localhost:27017/schema?maxPoolSize=100"/>
	</bean>

	<bean id="dataStorage" class="com.px100systems.data.core.TraditionalDatabase">
		<property name="runtimeStorage" ref="runtimeStorage"/>
		<property name="provider" ref="databaseProvider"/>

 		<property name="baseEntityPackages">
 			<list>
                <value>x.y.z</value>
 			</list>
 		</property>
 	</bean>
</beans> 	
```  
Mongo's URL format allows to conveniently define connection pool and other parameters along with the user name, password, and schema.
It should reside in some property file.    

The framework automatically syncs existing Mongo database schema with discovered entities and their indexes, creating and dropping 
collections and indexes, as needed on server startup.

## In-Memory Grids with Persistence
Going back to in-memory grids, they wouldn't be very useful if they didn't know how to populate the grid with data on startup and save it
to disk behind the scenes after updates. 

We decided against rudimentary built-in persistence facilities of Hazelcast and Ignite, that are geared more towards true (evictable) caches, 
and not the "main database" usage pattern of the grid. Here is our logic, primarily regarding Ignite's adherence to JCache (JSR107).

* Conceptually it is not a cache, so there are no read-through and other cache-specific operations.
* When used as a data grid, all data entries need to be loaded for queries to work, etc.
* JCache has no concept of redundant persister support: detecting stalled persistence, catching up after the persister restarts, etc.
* JCache has no concept of distributed multi-process loading when different nodes concurrently load different entities.

We evaluated three on-disk databases: MySQL, H2, and MapDB, and found MySQL lightning fast when used in a NoSQL manner: storing 
records as BLOBs. In general any NoSQL database (Mongo or MapDB) is slower than a "single-table" relational setup. You shouldn't look for a 
fancy database or ultimate performance to back the in-memory grid because the two batch operations: loading data on startup and asynchronous
incremental write-behind updates are not real-time. Write-through is, which is still several orders of magnitude faster than using the same 
database in a relational manner, and it (write-through) should only be used by developers on local workstations due to frequent server restarts.
Production deployments should mitigate data loss with data grid redundancy (backup nodes), which would be safe with moderate e.g. 2-minute
write-behind interval. Keep in mind, that several persistence servers are going to write-behind changes to several on-disk (e.g. MySQL) 
databases - another level of redundancy. In addition to that Px100 Data has robust measures to detect stalled persistence servers (aka "persisters")
and lets them catch up after the restart, as you can configure to keep the updates log for 24 hours or longer - doesn't cost much.
   
The configuration below uses a JDBC persister with MySQL. It is a typical developer's write-through setup.    
```xml
    ...  
  
    <bean id="mySqlService" class="com.px100systems.data.plugin.persistence.jdbc.TransactionalJdbcService">
        <property name="transactionManager" ref="mySqlTxManager"/>
    </bean>
    <bean id="mySqlDataSource" class="org.springframework.jdbc.datasource.DriverManagerDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/myschema"/>
        <property name="username" value="user"/>
        <property name="password" value="password"/>
    </bean>	
    <bean id="mySqlTxManager" class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
        <property name="dataSource" ref="mySqlDataSource"/>
    </bean>

    <bean id="persistenceProvider" class="com.px100systems.data.plugin.persistence.jdbc.JdbcPersistence">
        <property name="schemaName" value="myschema"/>
        <property name="unitStorageMapper">
            <bean class="com.px100systems.data.plugin.persistence.RegexUnitStorageMapper">
                <property name="mapping">
                    <map>
                        <entry key="MedicalInsuranceApplication" value="medium_table"/>
                        <entry key="MedicalInsuranceApplicationHistory" value="small_table"/>
                        <entry key="Something.*" value="big_table"/>
                    </map>
                </property>
            </bean>
        </property>
        <property name="storages">
            <list>
                <bean class="com.px100systems.data.plugin.persistence.jdbc.Storage">
                    <property name="table" value="big_table"/>
                    <property name="binaryType" value="BLOB"/>
                    <property name="blockSize" value="4096"/>
                    <property name="connection" ref="mySqlService"/>
                </bean>
                <bean class="com.px100systems.data.plugin.persistence.jdbc.Storage">
                    <property name="table" value="medium_table"/>
                    <property name="binaryType" value="BLOB"/>
                    <property name="blockSize" value="4096"/>
                    <property name="connection" ref="mySqlService"/>
                </bean>
                <bean class="com.px100systems.data.plugin.persistence.jdbc.Storage">
                    <property name="table" value="small_table"/>
                    <property name="binaryType" value="BLOB"/>
                    <property name="blockSize" value="2048"/>
                    <property name="connection" ref="mySqlService"/>
                </bean>
            </list>
        </property>
    </bean>

    <bean id="persistenceServer" class="com.px100systems.data.plugin.persistence.DiskPersistence">
        <property name="provider" ref="persistenceProvider"/>
    </bean>

    <bean id="storageProvider" class="com.px100systems.data.plugin.storage.ignite.IgniteInMemoryStorage">
        <property name="config" ref="igniteConfig"/>
        <property name="writeThrough" ref="persistenceProvider"/>
    </bean>
  
    <bean id="dataStorage" class="com.px100systems.data.core.InMemoryDatabase">
        <property name="runtimeStorage" ref="runtimeStorage"/>
    
        <property name="persistence" value="Load"/>
    
        <property name="baseEntityPackages">
            <list>
                <value>x.y.z</value>
            </list>
        </property>
    
        <property name="persistenceServer" ref="persistenceServer"/>
        <property name="backupDirectory" value="/some-directory"/>
    </bean>
</beans>
```
As you can recall, entities are stored in NoSQL "units", so the JDBC persister needs to map those "units" to SQL tables we call "storages".
The mapping is a Regex, not "*" wildcard.

If you are wondering what a typical storage e.g. "small_table" in the example above looks like, here's the DDL:
```sql
CREATE TABLE small_table (
  pk_id bigint(20) NOT NULL AUTO_INCREMENT,
  unit_name varchar(50) DEFAULT NULL,
  generator_name varchar(50) DEFAULT NULL,
  class_name varchar(100) DEFAULT NULL,
  id bigint(20) DEFAULT NULL,
  block_number int(11) DEFAULT NULL,
  data_size int(11) DEFAULT NULL,
  data blob,
  PRIMARY KEY (pk_id),
  KEY small_table_index0 (unit_name,id)
);
```
Storages store JSON blobs in blocks and are configured for entities of different size that need to use blocks of different (optimal) size.
Typically there are three storages of small, medium, and big block size, but you can get as granular, as you want. 
**Tip:** it is better to use the latter (big block storage) for development since big blocks would hold the entire JSON records w/o splitting them 
into several blocks - useful to examine human-readable JSON records in the database.

The two key configuration points above are:

1. Specifying writeThrough persister for the storageProvider that owns the data grid.
2. Telling the main database (dataStorage) to load the data on startup via persistence parameter.
 
If there is more than one persister in the cluster (during the startup), they'd load storages concurrently by picking them from the list. 
E.g. the first persister may start loading the big_table, and while it is doing so (locking it), the second persister will proceed to load 
the next storage in the list: medium_table, which it may finish loading sooner, than the first persister busy with the big_table. 
In that case the second persister starts loading the last storage: small_table.  
    
Write-behind configuration is a little more involved, as it deals with stalled persisters.
```xml
    <bean id="persistenceServer" class="com.px100systems.data.plugin.persistence.DiskPersistence">
        <property name="provider" ref="persistenceProvider"/>
 		<property name="writeBehindSeconds" value="120"/>
 		<property name="cleanupHours" value="25"/>
    </bean>

    <bean id="storageProvider" class="com.px100systems.data.plugin.storage.ignite.IgniteInMemoryStorage">
        <property name="config" ref="igniteConfig"/>
    </bean>
  
    <bean id="dataStorage" class="com.px100systems.data.core.InMemoryDatabase">
        <property name="runtimeStorage" ref="runtimeStorage"/>
    
        <property name="persistence" value="WriteBehind"/>
    
        <property name="baseEntityPackages">
            <list>
                <value>x.y.z</value>
            </list>
        </property>
    
        <property name="persistenceServer" ref="persistenceServer"/>
 		<property name="maxPersistenceDelayHours" value="24"/>
 		<property name="backupDirectory" value="/some-directory"/>
    </bean>
</beans>    
```    
There is no more persister for the in-memory storageProvider, however dataStorage persistence mode has changed to WriteBehind (which includes 
loading too of course). The rest of the parameters deal with some stalled persisters and emergency shutdown when every persister is down,
while the data grid itself is up.

The system will keep update log in the cluster for 25 hours (see cleanupHours) as specified above, so any stalled persisters can be restarted and catch up. 
If no persister has digested update log entries for more than 24 hours (see maxPersistenceDelayHours), it means all of them are down, which 
triggers the emergency shutdown: all cluster data being dumped to disk as files (one per "unit") - to the specified backupDirectory.

Px100 Data provides RestoreUtility to write all that data to the database, so the cluster can be started normally after that.
Emergency shutdown can also be invoked manually to perform data export/migration. 

Unlike relational databases in most cases there is no need for ALTER-based data migration when fields or entities are being added or deleted, 
as NoSQL adjusts automatically.   

## Dev Mode Initialization
Not as important, it is with relational databases, there is still a need for developers to periodically wipe out and recreate the database 
populating it with some "safe defaults". To do so (on server restart), specify dataStirage initializer. Once the server is restarted,
it is better to remove that param or comment it out, as it'd wipe out the database again on the next restart.

This equally applies to both SQL databases backing data grids and Mongo. 
```xml
    <bean id="dataStorage" class="com.px100systems.data.core.InMemoryDatabase">
        ...
        
 		<property name="initializer">
 			<bean class="x.y....MyInitializer"/>
 		</property>
    </bean>
</beans>  
```  
```java
public class MyInitializer implements DbInitializer {
	@Override
	public void initialize(Transaction tx) {
	    tx.insert(...);
	    tx.insert(...);
	    tx.insert(...);
	    tx.insert(...);
	    
	    ...
	    
	    tx.commit();
	}
```  
The Transaction passed to the initializer is for the default tenant zero, which is fine for single-tenant systems. 
You can start other Transactions for all of your tenants by calling tx.transaction(tenantId). Use the same approach (already described here)
to split it into batches, as the chances are you are going to insert a lot of entities.
  
Both Mongo and JDBC-connected persistence providers will create the schema with all tables (Mongo collections) automatically 
(or wipe out the existing one). All you need is an appropriate application user with sufficient privileges. 
