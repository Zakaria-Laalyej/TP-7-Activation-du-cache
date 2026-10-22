# TP 7 : Activation du cache de second niveau, optimisation des performances et suppression du problème N+1 avec JOIN FETCH et Entity Graph

**Réalisé par :** Zakaria Laalyej (2DSI)

Projet réalisé dans le cadre du module JEE / Persistance des données avec Hibernate et JPA.
Il met en œuvre l'activation et la configuration du cache de second niveau (L2 Cache), analyse l'impact du problème N+1 et démontre deux techniques majeures d'optimisation : la clause `JOIN FETCH` en JPQL et les `Entity Graphs` déclaratifs de JPA.

---

## Objectifs

- **Activer et configurer le cache de second niveau** : Partage d'entités en mémoire entre plusieurs contextes de persistance (`EntityManager`).
- **Identifier et mesurer le problème N+1** : Comprendre pourquoi le chargement tardif (`LAZY`) par défaut déclenche une explosion du nombre de requêtes SQL.
- **Résoudre le problème N+1 avec `JOIN FETCH`** : Rapatrier l'entité racine et ses relations en une seule et unique requête SQL.
- **Optimiser avec les Entity Graphs** : Utiliser `@NamedEntityGraph` pour spécifier déclarativement le graphe de chargement sans altérer les requêtes JPQL.
- **Collecter et analyser les métriques Hibernate** via l'API `Statistics` (`SecondLevelCacheHitCount`, `QueryExecutionCount`, etc.).

---

## Technologies

- **Java 17 / 21**
- **Maven**
- **Hibernate 5.6.15.Final** (JPA 2.2, `javax.persistence`)
- **H2 Database** (Base de données en mémoire haute performance)
- **JUnit 4.13.2**

---

## Modélisation des entités

```
                ┌────────────────────────┐
                │        Auteur          │
                ├────────────────────────┤
                │ - id : Long (PK)       │
                │ - nom : String         │
                │ - livres : List<Livre> │
                └───────────┬────────────┘
                            │ 1
                            │
                            │ * (mappedBy = "auteur", CascadeType.ALL)
                            ▼
                ┌────────────────────────┐
                │         Livre          │
                ├────────────────────────┤
                │ - id : Long (PK)       │
                │ - titre : String       │
                │ - auteur : Auteur      │
                └────────────────────────┘
```

- `@Entity` et `@Table` définissent le mapping relationnel.
- `@Cacheable` et `@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)` activent la mise en cache de second niveau pour `Auteur` et `Livre`.
- `@NamedEntityGraph(name = "Auteur.livres", attributeNodes = @NamedAttributeNode("livres"))` définit le graphe de chargement pour `Auteur`.

---

## Configuration du Cache (`persistence.xml` & `ehcache.xml`)

Dans `src/main/resources/META-INF/persistence.xml` :
```xml
<!-- Mode de cache sélectif pour respecter l'annotation @Cacheable -->
<shared-cache-mode>ENABLE_SELECTIVE</shared-cache-mode>

<properties>
    <!-- Activation du cache L2 et du cache de requêtes -->
    <property name="hibernate.cache.use_second_level_cache" value="true"/>
    <property name="hibernate.cache.use_query_cache" value="true"/>
    <property name="hibernate.generate_statistics" value="true"/>

    <!-- Fournisseur de région de cache -->
    <property name="hibernate.cache.region.factory_class" 
              value="com.example.cache.SimpleInMemoryRegionFactory"/>
</properties>
```

Le fichier `ehcache.xml` définit les politiques d'éviction et le dimensionnement mémoire :
- Taille maximale : 1000 entrées par entité.
- Expiration : TTL (Time To Live) de 60 minutes.

---

## Démonstration et Analyse des Performances

Lors de l'exécution de `App.java`, 10 auteurs et 50 livres sont initialisés dans la base H2.

### 1. Test sans optimisation : Problème N+1
Requête exécutée :
```sql
SELECT a FROM Auteur a
```
- **Requête initiale (1)** : Hibernate exécute 1 `SELECT` pour récupérer les 10 auteurs.
- **Requêtes secondaires (N = 10)** : Lors du parcours `auteur.getLivres().size()`, Hibernate déclenche 10 `SELECT` supplémentaires pour charger la collection `livres` de chaque auteur.
- **Total** : 11 requêtes SQL exécutées (**Durée : ~383 ms**).

### 2. Optimisation avec `JOIN FETCH`
Requête exécutée :
```sql
SELECT DISTINCT a FROM Auteur a JOIN FETCH a.livres
```
```sql
select distinct auteur0_.id as id1_0_0_, livres1_.id as id1_1_1_, auteur0_.nom as nom2_0_0_,
       livres1_.auteur_id as auteur_i3_1_1_, livres1_.titre as titre2_1_1_
from auteurs auteur0_ 
inner join livres livres1_ on auteur0_.id=livres1_.auteur_id
```
- **Résultat** : Toutes les données (auteurs et livres) sont rapatriées en **1 seule requête SQL**.
- **Gain** : **Durée réduite à ~60 ms** (plus de 6x plus rapide, 0 requête secondaire).

### 3. Optimisation déclarative avec Entity Graph
Code exécuté :
```java
EntityGraph<?> graph = em.getEntityGraph("Auteur.livres");
TypedQuery<Auteur> query = em.createQuery("SELECT a FROM Auteur a", Auteur.class);
query.setHint("javax.persistence.fetchgraph", graph);
```
```sql
select auteur0_.id as id1_0_0_, livres1_.id as id1_1_1_, auteur0_.nom as nom2_0_0_,
       livres1_.auteur_id as auteur_i3_1_1_, livres1_.titre as titre2_1_1_
from auteurs auteur0_ 
left outer join livres livres1_ on auteur0_.id=livres1_.auteur_id
```
- **Résultat** : Chargement complet en **1 seule requête SQL** sans modifier le JPQL de base.
- **Durée** : **~40 ms**.

### 4. Test du Cache de second niveau (L2 Cache)
- **1ère lecture** : Les entités sont chargées depuis la base et stockées dans le cache L2 (`60 L2C puts`).
- **2ème lecture** : Les entités sont directement résolues depuis le cache partagé (**Durée : ~4 ms**).
- **Accès inter-sessions** :
  ```java
  em.close(); // Fermeture de la session courante
  EntityManager em2 = emf.createEntityManager(); // Nouvelle session
  Auteur a = em2.find(Auteur.class, 1L); // Récupéré depuis L2 sans aucun SELECT SQL
  ```

---

## Rapport des Statistiques Hibernate

```text
====================== STATISTIQUES HIBERNATE ======================
Hits L2 Cache    : 1
Misses L2 Cache  : 10
Puts L2 Cache    : 60
Requetes SQL     : 5
=====================================================================
```

---

## Structure du projet

```
tp7-cache-nplus1/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/example/
    │   │   ├── App.java
    │   │   ├── cache/
    │   │   │   └── SimpleInMemoryRegionFactory.java
    │   │   ├── model/
    │   │   │   ├── Auteur.java
    │   │   │   └── Livre.java
    │   │   └── service/
    │   │       ├── DataInitService.java
    │   │       └── PerformanceTestService.java
    │   └── resources/
    │       ├── META-INF/persistence.xml
    │       └── ehcache.xml
    └── test/java/com/example/
        └── AppTest.java
```

---

## Commandes d'exécution

### Lancer les tests unitaires JUnit
```bash
mvn test
```

### Compiler le projet
```bash
mvn clean compile
```
### Exécution de la classe principale App
<img width="1805" height="837" alt="image" src="https://github.com/user-attachments/assets/cfb21265-e172-4b7d-8fd7-f26fa15ff803" />


## Auteur

**Zakaria Laalyej**, 2DSI, 2026.
