
package com.bankingplatform.loan.model;
import jakarta.persistence.*;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="loans")
public class Loan {
    public enum Status { APPLIED, UNDER_REVIEW, APPROVED, ACTIVE, REJECTED, CLOSED }
    @Id private UUID id;
    @Column(name="customer_id", nullable=false) private UUID customerId;
    @Column(name="product_code", nullable=false) private String productCode;
    @Column(nullable=false, precision=19, scale=4) private BigDecimal principal;
    @Column(name="interest_rate", nullable=false, precision=8, scale=4) private BigDecimal interestRate;
    @Column(name="term_months", nullable=false) private int termMonths;
    @Column(name="monthly_payment", nullable=false, precision=19, scale=4) private BigDecimal monthlyPayment;
    @Column(name="outstanding_balance", nullable=false, precision=19, scale=4) private BigDecimal outstandingBalance;
    @Column(nullable=false, length=3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status = Status.APPLIED;
    private String purpose;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @PrePersist void onCreate(){ if(id==null) id=UUID.randomUUID(); Instant n=Instant.now(); createdAt=n; updatedAt=n; }
    @PreUpdate void onUpdate(){ updatedAt=Instant.now(); }
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public UUID getCustomerId(){return customerId;} public void setCustomerId(UUID c){this.customerId=c;}
    public String getProductCode(){return productCode;} public void setProductCode(String p){this.productCode=p;}
    public BigDecimal getPrincipal(){return principal;} public void setPrincipal(BigDecimal p){this.principal=p;}
    public BigDecimal getInterestRate(){return interestRate;} public void setInterestRate(BigDecimal r){this.interestRate=r;}
    public int getTermMonths(){return termMonths;} public void setTermMonths(int t){this.termMonths=t;}
    public BigDecimal getMonthlyPayment(){return monthlyPayment;} public void setMonthlyPayment(BigDecimal m){this.monthlyPayment=m;}
    public BigDecimal getOutstandingBalance(){return outstandingBalance;} public void setOutstandingBalance(BigDecimal o){this.outstandingBalance=o;}
    public String getCurrency(){return currency;} public void setCurrency(String c){this.currency=c;}
    public Status getStatus(){return status;} public void setStatus(Status s){this.status=s;}
    public String getPurpose(){return purpose;} public void setPurpose(String p){this.purpose=p;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
