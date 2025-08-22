package kr.hhplus.be.server.util;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Offset 기반 페이지네이션을 위한 Pageable 구현체
 * -> Spring Data JPA의 기본 PageRequest는 페이지 번호 기반
 * 
 * @see Pageable
 */
public class OffsetBasedPageRequest implements Pageable {
    
    private final int offset;
    private final int limit;
    private final Sort sort;
    
    /**
     * @param offset 
     * @param limit 
     * @param sort 정렬 조건
     * @throws IllegalArgumentException 
     */
    public OffsetBasedPageRequest(int offset, int limit, Sort sort) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative!");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be greater than zero!");
        }
        this.offset = offset;
        this.limit = limit;
        this.sort = sort != null ? sort : Sort.unsorted();
    }
    
    /**
     * @param offset
     * @param limit
     */
    public OffsetBasedPageRequest(int offset, int limit) {
        this(offset, limit, Sort.unsorted());
    }
    
    @Override
    public int getPageNumber() {
        // 페이지 번호 계산: offset / limit
        return offset / limit;
    }
    
    @Override
    public int getPageSize() {
        return limit;
    }
    
    @Override
    public long getOffset() {
        // SQL OFFSET 그대로 반환
        return offset;
    }
    
    @Override
    public Sort getSort() {
        return sort;
    }
    
    @Override
    public Pageable next() {
        return new OffsetBasedPageRequest(offset + limit, limit, sort);
    }
    
    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() 
            ? new OffsetBasedPageRequest(Math.max(0, offset - limit), limit, sort)
            : first();
    }
    
    @Override
    public Pageable first() {
        return new OffsetBasedPageRequest(0, limit, sort);
    }
    
    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetBasedPageRequest(pageNumber * limit, limit, sort);
    }
    
    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
    
    @Override
    public boolean isPaged() {
        return true;
    }
    
    @Override
    public boolean isUnpaged() {
        return false;
    }
    
    @Override
    public Sort getSortOr(Sort sort) {
        return this.sort.isSorted() ? this.sort : sort;
    }
    
    @Override
    public String toString() {
        return String.format("OffsetBasedPageRequest [offset=%d, limit=%d, sort=%s]", 
                            offset, limit, sort);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OffsetBasedPageRequest)) {
            return false;
        }
        OffsetBasedPageRequest that = (OffsetBasedPageRequest) obj;
        return this.offset == that.offset && 
               this.limit == that.limit && 
               this.sort.equals(that.sort);
    }
    
    @Override
    public int hashCode() {
        int result = offset;
        result = 31 * result + limit;
        result = 31 * result + sort.hashCode();
        return result;
    }
}