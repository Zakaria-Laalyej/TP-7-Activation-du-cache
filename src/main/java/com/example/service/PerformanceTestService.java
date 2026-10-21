package com.example.service;

import com.example.model.Auteur;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

public class PerformanceTestService {

    public EntityManager em;

    public PerformanceTestService() {
    }

    public PerformanceTestService(EntityManager em) {
        this.em = em;
    }

    /**
     * Etape 7 : Mesure sans optimisation (Probleme N+1).
     * 1 requete pour charger N auteurs, puis N requetes secondaires pour charger la collection 'livres'.
     */
    public void testPerformanceSansCache() {
        long start = System.currentTimeMillis();

        TypedQuery<Auteur> query = em.createQuery("SELECT a FROM Auteur a", Auteur.class);
        List<Auteur> auteurs = query.getResultList();

        int totalLivres = 0;
        // Acces a la collection LAZY : declenche 1 requete SQL par auteur (Probleme N+1)
        for (Auteur auteur : auteurs) {
            totalLivres += auteur.getLivres().size();
        }

        long end = System.currentTimeMillis();
        System.out.println("-> [Sans optimisation / N+1] Duree : " + (end - start) + " ms | " +
                auteurs.size() + " auteurs, " + totalLivres + " livres charges.");
    }

    /**
     * Etape 8 : Suppression du probleme N+1 avec JOIN FETCH.
     * 1 seule requete SQL avec jointure INNER/LEFT JOIN pour tout rapatrier.
     */
    public void testPerformanceAvecJoinFetch() {
        long start = System.currentTimeMillis();

        TypedQuery<Auteur> query = em.createQuery(
                "SELECT DISTINCT a FROM Auteur a JOIN FETCH a.livres", Auteur.class);
        List<Auteur> auteurs = query.getResultList();

        int totalLivres = 0;
        for (Auteur auteur : auteurs) {
            totalLivres += auteur.getLivres().size();
        }

        long end = System.currentTimeMillis();
        System.out.println("-> [JOIN FETCH] Duree : " + (end - start) + " ms | " +
                auteurs.size() + " auteurs, " + totalLivres + " livres en 1 seule requete.");
    }

    /**
     * Etape 9 : Optimisation declarative avec Entity Graph.
     * Permet de charger les relations sans alterer la requete JPQL de base.
     */
    public void testPerformanceAvecEntityGraph() {
        long start = System.currentTimeMillis();

        EntityGraph<?> graph = em.getEntityGraph("Auteur.livres");
        TypedQuery<Auteur> query = em.createQuery("SELECT a FROM Auteur a", Auteur.class);
        query.setHint("javax.persistence.fetchgraph", graph);
        List<Auteur> auteurs = query.getResultList();

        int totalLivres = 0;
        for (Auteur auteur : auteurs) {
            totalLivres += auteur.getLivres().size();
        }

        long end = System.currentTimeMillis();
        System.out.println("-> [Entity Graph] Duree : " + (end - start) + " ms | " +
                auteurs.size() + " auteurs charges via le graphe 'Auteur.livres'.");
    }

    /**
     * Etape 10 : Test du Cache de second niveau.
     * Les entites mises en cache par l'EntityManagerFactory sont reutilisees sans requete SQL.
     */
    public void testPerformanceAvecCache() {
        // Premier chargement (remplit le cache L2)
        testPerformanceAvecJoinFetch();

        // Deuxieme chargement : les entites Auteur et Livre sont dans le cache de 2nd niveau
        long start = System.currentTimeMillis();
        TypedQuery<Auteur> query = em.createQuery(
                "SELECT DISTINCT a FROM Auteur a JOIN FETCH a.livres", Auteur.class);
        List<Auteur> auteurs = query.getResultList();
        for (Auteur a : auteurs) {
            a.getLivres().size();
        }
        long end = System.currentTimeMillis();
        System.out.println("-> [Cache L2 - Lecture 2] Duree : " + (end - start) + " ms (recuperees du cache).");
    }
}
