package com.example;

import com.example.model.Auteur;
import com.example.service.DataInitService;
import com.example.service.PerformanceTestService;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class App {

    public static void main(String[] args) {
        System.out.println("=======================================================================");
        System.out.println("  TP 7 : Cache de second niveau, Optimisation et Elimination du N+1");
        System.out.println("=======================================================================");

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("hibernate-cache-pu");
        EntityManager em = emf.createEntityManager();

        DataInitService dataInitService = new DataInitService(em);
        PerformanceTestService performanceTestService = new PerformanceTestService(em);

        // 1. Initialisation des donnees
        System.out.println("\n--- [1] Initialisation du jeu d'essai ---");
        dataInitService.initData();

        // Nettoyage du cache de premier niveau (Session) pour forcer les tests
        em.clear();

        // 2. Test sans cache (probleme N+1)
        System.out.println("\n--- [2] Test sans optimisation : Probleme N+1 (Lazy loading) ---");
        performanceTestService.testPerformanceSansCache();

        em.clear();

        // 3. Test avec JOIN FETCH
        System.out.println("\n--- [3] Test d'optimisation : JOIN FETCH ---");
        performanceTestService.testPerformanceAvecJoinFetch();

        em.clear();

        // 4. Test avec Entity Graph
        System.out.println("\n--- [4] Test d'optimisation : JPA Entity Graph ---");
        performanceTestService.testPerformanceAvecEntityGraph();

        em.clear();

        // 5. Test du Cache de second niveau
        System.out.println("\n--- [5] Test du Cache de second niveau (L2) ---");
        performanceTestService.testPerformanceAvecCache();

        // 6. Test d'acces unitaire inter-sessions via L2 Cache
        System.out.println("\n--- [6] Verification unitaire du Cache de second niveau inter-sessions ---");
        em.close();

        // Nouvelle session (EntityManager distinct)
        EntityManager em2 = emf.createEntityManager();
        System.out.println("Ouverture d'une nouvelle session EntityManager...");
        long t1 = System.currentTimeMillis();
        Auteur auteurCached = em2.find(Auteur.class, 1L);
        long t2 = System.currentTimeMillis();
        System.out.println("Auteur recupere : " + auteurCached.getNom() + " (en " + (t2 - t1) + " ms depuis L2 Cache)");

        // Statistiques Hibernate
        Session session = em2.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();
        System.out.println("\n====================== STATISTIQUES HIBERNATE ======================");
        System.out.println("Hits L2 Cache    : " + stats.getSecondLevelCacheHitCount());
        System.out.println("Misses L2 Cache  : " + stats.getSecondLevelCacheMissCount());
        System.out.println("Puts L2 Cache    : " + stats.getSecondLevelCachePutCount());
        System.out.println("Requetes SQL     : " + stats.getQueryExecutionCount());
        System.out.println("=====================================================================");

        em2.close();
        emf.close();
        System.out.println("\nExecution du TP 7 terminee avec succes !");
    }
}
