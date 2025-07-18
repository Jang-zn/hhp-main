package kr.hhplus.be.server.adapter.storage.inmemory;

import kr.hhplus.be.server.domain.entity.Product;
import kr.hhplus.be.server.domain.port.storage.ProductRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryProductRepository implements ProductRepositoryPort {
    
    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    
    /**
     * Retrieves a product by its ID from the in-memory store.
     *
     * @param id the unique identifier of the product
     * @return an {@code Optional} containing the product if found, or empty if not present
     */
    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id));
    }
    
    /**
     * Returns a list of all products currently stored in memory.
     *
     * The limit and offset parameters are accepted but not applied; pagination is not implemented.
     *
     * @param limit the maximum number of products to return (not used)
     * @param offset the starting index for products to return (not used)
     * @return a list containing all products in the repository
     */
    @Override
    public List<Product> findAll(int limit, int offset) {
        // TODO: 페이징 로직 구현
        return new ArrayList<>(products.values());
    }
    
    /**
     * Saves the given product to the in-memory store, inserting or updating it by its ID.
     *
     * @param product the product to be saved
     * @return the saved product
     */
    @Override
    public Product save(Product product) {
        products.put(product.getId(), product);
        return product;
    }
    
    /**
     * Retrieves a product by its ID and is intended to update its stock and reserved stock values.
     * <p>
     * The actual stock update logic is not implemented; the method currently returns the product if found, or {@code null} otherwise.
     *
     * @param productId      the ID of the product to update
     * @param stock          the new stock value (not currently applied)
     * @param reservedStock  the new reserved stock value (not currently applied)
     * @return the product with the specified ID, or {@code null} if not found
     */
    @Override
    public Product updateStock(Long productId, int stock, int reservedStock) {
        Product product = products.get(productId);
        if (product != null) {
            // TODO: 실제 업데이트 로직 구현
        }
        return product;
    }
    
    /**
     * Returns a list of popular products within the specified period.
     *
     * @param period the time period (in an implementation-defined unit) to consider for product popularity
     * @return a list of popular products; currently always returns an empty list
     */
    @Override
    public List<Product> findPopularProducts(int period) {
        // TODO: 인기 상품 조회 로직 구현
        return new ArrayList<>();
    }
} 