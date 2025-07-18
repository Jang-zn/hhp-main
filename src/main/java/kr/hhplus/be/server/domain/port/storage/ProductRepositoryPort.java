package kr.hhplus.be.server.domain.port.storage;

import kr.hhplus.be.server.domain.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {
    /**
 * Retrieves a product by its unique identifier.
 *
 * @param id the unique identifier of the product
 * @return an {@code Optional} containing the product if found, or empty if not found
 */
Optional<Product> findById(Long id);
    /**
 * Retrieves a paginated list of products.
 *
 * @param limit  the maximum number of products to return
 * @param offset the starting index from which to retrieve products
 * @return a list of products based on the specified pagination parameters
 */
List<Product> findAll(int limit, int offset);
    /**
 * Persists a new product entity and returns the saved product.
 *
 * @param product the product to be saved
 * @return the saved product entity
 */
Product save(Product product);
    /**
 * Updates the stock and reserved stock quantities for the specified product.
 *
 * @param productId      the unique identifier of the product to update
 * @param stock          the new available stock quantity
 * @param reservedStock  the new reserved stock quantity
 * @return the updated Product entity
 */
Product updateStock(Long productId, int stock, int reservedStock);
    /**
 * Retrieves a list of popular products within the specified time period.
 *
 * @param period the time period (in days) to consider for determining product popularity
 * @return a list of popular products for the given period
 */
List<Product> findPopularProducts(int period);
} 