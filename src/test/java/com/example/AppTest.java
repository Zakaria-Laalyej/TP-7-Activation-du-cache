package com.example;

import com.example.model.Auteur;
import com.example.model.Livre;
import com.example.service.DataInitService;
import com.example.service.PerformanceTestService;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.List;

public class AppTest {

    private static EntityManagerFactory emf;

    @BeforeClass
    public static void setUp() {
        emf = Persistence.createEntityManagerFactory("hibernate-cache-pu");
        EntityManager em = emf.createEntityManager();
        DataInitService initService = new DataInitService(em);
        initService.initData();
        em.close();
    }

    @AfterClass
    public static void tearDown() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @Test
    public void testJoinFetchElimineNPlusUn() {
        EntityManager em = emf.createEntityManager();
        List<Auteur> auteurs = em.createQuery(
                "SELECT DISTINCT a FROM Auteur a JOIN FETCH a.livres", Auteur.class)
                .getResultList();

        Assert.assertNotNull(auteurs);
        Assert.assertEquals(10, auteurs.size());
        Assert.assertEquals(5, auteurs.get(0).getLivres().size());
        em.close();
    }

    @Test
    public void testEntityGraphElimineNPlusUn() {
        EntityManager em = emf.createEntityManager();
        EntityGraph<?> graph = em.getEntityGraph("Auteur.livres");
        List<Auteur> auteurs = em.createQuery("SELECT a FROM Auteur a", Auteur.class)
                .setHint("javax.persistence.fetchgraph", graph)
                .getResultList();

        Assert.assertNotNull(auteurs);
        Assert.assertTrue(auteurs.size() >= 10);
        Assert.assertFalse(auteurs.get(0).getLivres().isEmpty());
        em.close();
    }

    @Test
    public void testSecondLevelCacheHits() {
        // Session 1 : Chargement et mise en cache L2
        EntityManager em1 = emf.createEntityManager();
        Auteur a1 = em1.find(Auteur.class, 1L);
        Assert.assertNotNull(a1);
        em1.close();

        // Session 2 : Doit etre servi par le cache L2 sans requete
        EntityManager em2 = emf.createEntityManager();
        Auteur a2 = em2.find(Auteur.class, 1L);
        Assert.assertNotNull(a2);
        Assert.assertEquals(a1.getNom(), a2.getNom());

        Session session = em2.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();
        System.out.println("AppTest L2 Cache Hits: " + stats.getSecondLevelCacheHitCount());
        Assert.assertTrue("Le cache de 2nd niveau doit enregistrer au moins 1 hit",
                stats.getSecondLevelCacheHitCount() >= 1);
        em2.close();
    }
}
