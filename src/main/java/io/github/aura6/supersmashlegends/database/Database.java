package io.github.aura6.supersmashlegends.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Database {
    private MongoCollection<Document> mongoCollection;

    public void init(String uri, String db, String collection) {
        this.mongoCollection = MongoClients.create(uri).getDatabase(db).getCollection(collection);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(UUID uuid, String key, T ifNull, T ifDisabled) {
        if (mongoCollection == null) return ifDisabled;

        CompletableFuture<T> future = new CompletableFuture<>();
        Document doc = mongoCollection.find(new Document("uuid", uuid.toString())).first();
        future.complete(doc == null || doc.get(key) == null ? null : (T) doc.get(key));

        try {
            T result = future.get();
            return result == null ? ifNull : result;

        } catch (InterruptedException | ExecutionException e) {
            return ifNull;
        }
    }

    public <T> void setIfEnabled(UUID uuid, String key, T value) {
        if (mongoCollection == null) return;

        Document document = new Document("uuid", uuid.toString());
        Document existing = mongoCollection.find(document).first();

        if (existing == null) {
            document.put(key, value);
            mongoCollection.insertOne(document);

        } else {
            mongoCollection.updateOne(existing, Updates.set(key, value));
        }
    }

    public void increaseDouble(UUID uuid, String key, double amount) {
        double oldDouble = this.getOrDefault(uuid, key, 0.0, 0.0);
        this.setIfEnabled(uuid, key, oldDouble + amount);
    }

    public void increaseInt(UUID uuid, String key, int amount) {
        int oldInt = this.getOrDefault(uuid, key, 0, 0);
        this.setIfEnabled(uuid, key, oldInt + amount);
    }

    public List<Document> getDocuments() {
        if (mongoCollection == null) return Collections.emptyList();

        List<Document> documents = new ArrayList<>();
        FindIterable<Document> found = mongoCollection.find();

        for (Document document : found) {
            documents.add(document);
        }

        return documents;
    }
}
