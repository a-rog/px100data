# Px100 Data Framework Concepts
## Origins and Purpose
**Px100 Data** is the lower persistence-centric part of the larger [Px100 Platform](http://px100systems.com). 
It provides an elegant unified API for using in-memory data-grids like Hazelcast and Ignite adding transparent automatic on-disk persistence:
choice of write-behind and write-through (also distributed/redundant). It also wraps traditional on-disk databases like Mongo. In essence
Px100 data is a NoSQL JDBC-like layer for data-grids and databases like Mongo. 

Px100 stands for Productivity x100. It achieves programmer's productivity by eliminating CRUD, N-tier architecture with its "facades" and "delegates", 
and other IT "conventional wisdoms". Px100 views Internet-hosted SaaS systems as traditional object-oriented programs e.g. Java Swing ones
comprised of smart fine-grained objects - alive and interacting with each other, as OOP originally envisioned, rather than dumb DTOs being passed 
up and down by undeniably procedural "stateless" "services". 
 
Px100 Data role is to provide the scalable and fault-tolerant permanent transactional storage for those smart entities. Note the term "storage" - 
not "database". In-memory data grid based Px100 Data is, in our opinion of course, the closest thing to the true limitless, persistent, and 
transactional "RAM" trillions of smart connected OOP citizens (objects) can live in w/o the need to load or save themselves from/to disk via  
bulk data transfers. 

After all, the only data-centric application problem that matters: modeling complex business workflows and processors has nothing to do with
data persistence logistics: passing data back and forth between the client and server, server and the database, packing and unpacking it, 
transformations between DTO formats (e.g. strongly-typed Java vs. JSON) and other tedious CRUD plumbing. CRUD code shouldn't be auto-generated,
prepackaged, or hidden. It simply has to go. Data shouldn't be dumb, and it shouldn't be transported along application "layers".
 
It should simply live in the RAM w/o explicit loading or saving. And by "living" we mean interacting with other smart objects that encapsulate
their own data aka *state*, as well, as *identity* and *behavior*, OOP "founding fathers": Bjarne Stroustrup and Grady Booch originally 
envisioned - not (using C concepts) "structs": DTOs processed by "functions": service methods. Px100 Data is the first step towards 
the true Object-Oriented CRUD-less future. No databases. Just limitless RAM that survives server restarts and is updated in a 
transactional manner. 

Px100 Platform takes Px100 Data approach further - eliminating JSON/Java conversion and other DTO logic at the higher, UI levels, all of which
(levels and layers) written in the same programming language: Java with infusion of interpreted Groovy. The same "smart" and "live" object model
lives across the entire application with its screens, workflows, and security. While different modules can require different models of the same
data (rare in a well-thought application), physical and worse, self-proclaimed "we always layer application like this" tiers should not. 

Both the end user, and the programmer should view SaaS systems as logically modular, yet layer-less whole. Again, think of the whole browser-based 
SaaS application as traditional desktop (e.g. Swing) one, yet transparently distributed for high availability and performance reasons. 
The exact physical deployment, as well as what it takes to "load" and "save" the data to some persistent storage shouldn't matter at all for 
the software developer, just like it doesn't matter for the end user. Logical modularization (if any) should be dictated by business logic and business 
needs only. You can read more on the concept of limitless persistent RAM [here](https://www.linkedin.com/pulse/3rd-generation-data-persistence-alex-rogachevsky).  

## General Architecture        
Px100 Data was originally envisioned as a JDBC-like abstraction of in-memory data grids like Hazelcast and Ignite used as primary data storage
and backed by transparent automatic write-through or write-behind pluggable on-disk database persistence. Later support for traditional on-disk 
databases was added: Mongo being the only storage provider of that sort at the moment.
    
Px100 API is completely oblivious to the underlying implementation. It has its own model and guarantees e.g. transactional commit and rollback 
for non-transactional storage providers like Mongo.    

Px100 Data, like Px100 Platform in general strives for simplicity and minimalism: the most concise APIs w/o unneeded features to satisfy the entire world.
There is no fancy AOP, runtime bytecode generation, behind the scene proxies, and class substitutions. It is simple and down to Earth. 
Fully debuggable too. 
    
Read the detailed User Guide and see DatabaseStorage class for details.
  
Using Px100 is essentially:

* configuring the DatabaseStorage instance in Spring by specifying on of three storage providers:
    * on-disk MongoDatabaseStorage - the easiest one since it doesn't need additional write-through or write-behind persistence
    * in-memory HazelcastMemoryStorage
    * in-memory IgniteInMemoryStorage
* telling DatabaseStorage whwre your entity classes are by specifying packages to scan.    
* calling DatabaseStorage methods - primarily transaction() to obtain a Transaction and call its methods including the last one: commit().
      
An in-memory storage providers are still used with traditional databases to store cluster-wide "transient" (runtime only) data. However
if you want (and should) to use it as your primary "database", you should plug in a persistence provider responsible for loading the data 
on in-memory cluster startup and saving it in a (configurable) write-through or write-behind manner. See JdbcPersistence class for details.
   
Generally you should pick one provider and stick with it. However, as crazy, as it sounds, if you wish to do something like that mid-project:

* switching between in-memory providers e.g. Hazelcast and Ignite is completely transparent - just change the config. No data loss.
* switching between in-memory and traditional providers would cost you the data, which can still be migrated between Mongo and 
the on-disk database backing the in-memory data grid. YOu can use our data dump/restore utility. 
See InMemoryDatabase.emergencyShutdown() that dumps data to disk files (can be invoked manually) and RestoreUtility.
You can also easily write your own universal cluster data migration tool under 20 lines of code. We didn't beacuse we consider switching between 
Hazelcast/Ignite and Mongo highly unlikely. There is nothing worse, than unneeded code written just in case.        

In any case nothing changes usage-wise: the same API and the same entities (data types, etc.)

The architecture is open, and you are welcome to write a storage provider for a new in-memory data grid or on-disk NoSQL database. 

Px100 Data doesn't compromize, when it comes to performance - leveraging all available provider optimizations e.g. indexes. You can be assured
you are using 100% of Hazelcast, Ignite, or Mongo. The latter is enough for small to moderate systems and with some tricks even terabytes of 
data, while Ignite scales well out of the box at the cost of memory, which (hardware) should never be a limiting factor. Hazelcast currently 
lags Ignite in query speed being faster when it comes to updates, and also using less memory. The choice is yours. Data grids like Hazelcast and 
Ignite are infinitely scalable horizontally - just add new servers/nodes.     

Whatever provider you choose, just like JDBC, Px100 Data requires you to learn your "database" configuration- and tuning-wise. We provided 
rich and comprehensive configuration samples for all three in the User Guide: the actual Spring configuration we use in production for 
Px100-built systems. Use it as a starting point.
  
## What Px100 Data Can Do for You
No more DTOs. Your entities can (and should) be as complex, as needed - encapsulating sub-objects, collections (Lists and Sets), 
and if that is not enough (rare), anything else like Maps can be made serializable via custom getters e.g. converting a Map to a List of beans.
NoSQL means storing indexed BLOBs. It can store anything in its most natural (serialized) object-oriented form w/o the awkward ORM process to 
assemble "objects" out of multiple tables.     

No data migration when you add or remove fields and even entire entities - natural for Document structure agnostic Mongo and data grids.
    
Orthogonal and minimalistic API with criteria queries.    

## Installation
Use the following Maven repository *(temporary measure - until the project is added to Maven Central)*
```xml
    <repositories>
        <repository>
            <id>Px100 Data GitHub Repo</id>
            <url>https://oss.sonatype.org/content/groups/public</url>
        </repository>
    </repositories>
```

**Core Dependency**
```xml
    <dependency>
        <groupId>com.px100systems</groupId>
        <artifactId>px100-persistence</artifactId>
        <version>0.3.0</version>
    </dependency>
```

Then add one of the following:

**Persistent Ignite**
```xml
    <dependency>
        <groupId>com.px100systems</groupId>
        <artifactId>ignite-storage</artifactId>
        <version>0.3.0</version>
    </dependency>
    <dependency>
        <groupId>com.px100systems</groupId>
        <artifactId>in-memory-jdbc-persistence</artifactId>
        <version>0.3.0</version>
    </dependency>
```

**Persistent Hazelcast**
```xml
    <dependency>
        <groupId>com.px100systems</groupId>
        <artifactId>hazelcast-storage</artifactId>
        <version>0.3.0</version>
    </dependency>
    <dependency>
        <groupId>com.px100systems</groupId>
        <artifactId>in-memory-jdbc-persistence</artifactId>
        <version>0.3.0</version>
    </dependency>
```

**Mongo**
```xml
    <dependency>
        <groupId>com.px100systems</groupId>
        <artifactId>mongo-storage</artifactId>
        <version>0.3.0</version>
    </dependency>
```

## Learn Px100 Data
See the User Guide
