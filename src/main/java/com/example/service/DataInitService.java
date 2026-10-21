package com.example.service;

import com.example.model.Auteur;
import com.example.model.Livre;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

public class DataInitService {

    public EntityManager em;

    public DataInitService() {
    }

    public DataInitService(EntityManager em) {
        this.em = em;
    }

    public void initData() {
        EntityTransaction tx = em.getTransaction();
        boolean localTx = !tx.isActive();
        if (localTx) tx.begin();

        for (int i = 1; i <= 10; i++) {
            Auteur auteur = new Auteur("Auteur " + i);
            for (int j = 1; j <= 5; j++) {
                Livre livre = new Livre("Livre " + j + " de l'auteur " + i);
                auteur.ajouterLivre(livre);
            }
            em.persist(auteur);
        }

        if (localTx) tx.commit();
        System.out.println("[DataInitService] 10 auteurs et 50 livres inseres avec succes.");
    }
}
