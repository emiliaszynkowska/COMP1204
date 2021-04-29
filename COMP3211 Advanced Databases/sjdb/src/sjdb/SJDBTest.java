package sjdb;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class SJDBTest {

	public static Catalogue createCatalogue() {
		Catalogue cat = new Catalogue();
		cat.createRelation("A", 100);
		cat.createAttribute("A", "a1", 100);
		cat.createAttribute("A", "a2", 15);
		cat.createRelation("B", 150);
		cat.createAttribute("B", "b1", 150);
		cat.createAttribute("B", "b2", 100);
		cat.createAttribute("B", "b3", 5);
		cat.createRelation("C", 200);
		cat.createAttribute("C", "c1", 7);
		cat.createAttribute("C", "c2", 13);
		cat.createAttribute("C", "c3", 5);
		
		return cat;
	}

	@Test
	void originalTestCase() throws DatabaseException {
		System.out.println("--- NEW TEST ---");
		Scan a = new Scan(createCatalogue().getRelation("A"));
		Scan b = new Scan(createCatalogue().getRelation("B")); 
		
		Product p1 = new Product(a, b);
		
		Select s1 = new Select(p1, new Predicate(new Attribute("a2"), new Attribute("b3")));
		
		ArrayList<Attribute> atts = new ArrayList<Attribute>();
		atts.add(new Attribute("a2"));	
		atts.add(new Attribute("b1"));	
		
		Project plan = new Project(s1, atts);
		
		Inspector inspector = new Inspector();
		Estimator estimator = new Estimator();
		
		plan.accept(estimator);
		plan.accept(inspector);
		
		assertEquals(plan.getOutput().getTupleCount(), 1000);
		assert(plan.getOutput().getAttributes().size() == 2);
		assert(plan.getOutput().getAttributes().contains(new Attribute("a2")));
		assert(plan.getOutput().getAttributes().contains(new Attribute("b1")));
		System.out.println("--- NEW TEST ---");
	}
	
	@Test
	void testSelectWithValue() throws DatabaseException {
		System.out.println("--- NEW TEST ---");
		Scan a = new Scan(createCatalogue().getRelation("A"));
		
		Select select = new Select(a, new Predicate(new Attribute("a2"), "value"));
		
		Inspector inspector = new Inspector();
		Estimator estimator = new Estimator();
		
		select.accept(estimator);
		select.accept(inspector);
		
		assert(select.getOutput().getTupleCount() == 7);
		assert(select.getOutput().getAttribute(new Attribute("a2")).getValueCount() == 1);
		assert(select.getOutput().getAttribute(new Attribute("a1")).getValueCount() == 7);
		
		System.out.println("--- NEW TEST ---");
	}
	
	@Test
	void selectValueOnProduct() throws DatabaseException {
		System.out.println("--- NEW TEST ---");
		Scan a = new Scan(createCatalogue().getRelation("A"));
		Scan b = new Scan(createCatalogue().getRelation("B")); 
		
		Product product = new Product(a, b);
		
		Select select = new Select(product, new Predicate(new Attribute("b3"), "value"));
		
		Inspector inspector = new Inspector();
		Estimator estimator = new Estimator();
		
		select.accept(estimator);
		select.accept(inspector);
		
		assert(select.getOutput().getAttribute(new Attribute("b3")).getValueCount() == 1);
		
		double expectedOutput = (double) select.getInput().getOutput().getTupleCount() / select.getInput().getOutput().getAttribute(new Attribute("b3")).getValueCount();
		
		assertEquals(select.getOutput().getTupleCount(), (int) Math.ceil(expectedOutput));
		
		System.out.println("--- NEW TEST ---");
	}
	
	@Test
	void simpleJoinTest() throws DatabaseException {
		System.out.println("--- NEW TEST ---");
		Scan a = new Scan(createCatalogue().getRelation("A"));
		Scan b = new Scan(createCatalogue().getRelation("B"));
		
		Join join = new Join(a, b, new Predicate(new Attribute("a1"), new Attribute("b1")));
		
		Inspector inspector = new Inspector();
		Estimator estimator = new Estimator();
		
		join.accept(estimator);
		join.accept(inspector);
		
		assertEquals(join.getOutput().getTupleCount(), 100);
		assertEquals(join.getOutput().getAttribute(new Attribute("a1")).getValueCount(), join.getOutput().getAttribute(new Attribute("b1")).getValueCount());
		
	}
	
	@Test void doubleProductProject() throws DatabaseException {
		System.out.println("--- doubleProductProject---");
		
		Scan a = new Scan(createCatalogue().getRelation("A"));
		Scan b = new Scan(createCatalogue().getRelation("B"));
		Scan c = new Scan(createCatalogue().getRelation("C"));
		
		Product ab = new Product(a,b);
		Product abc = new Product(ab, c);
		
		Inspector inspector = new Inspector();
		Estimator estimator = new Estimator();
		
		abc.accept(estimator);
		abc.accept(inspector);
		
		assertEquals(abc.getOutput().getTupleCount(), a.getOutput().getTupleCount() * b.getOutput().getTupleCount() * c.getOutput().getTupleCount());
		
		System.out.println("--- NEW TEST ---");
	}
	
	@Test void productProjectJoinSelectAttributeThree() throws DatabaseException {
		System.out.println("--- productProjectSelectAttributeThree ---");
		
		Scan a = new Scan(createCatalogue().getRelation("A"));
		Scan b = new Scan(createCatalogue().getRelation("B"));
		Scan c = new Scan(createCatalogue().getRelation("C"));
		
		Product bc = new Product(b, c);
		
		ArrayList<Attribute> attrs = new ArrayList<>();
		attrs.add(new Attribute("b1"));
		attrs.add(new Attribute("c2"));
		attrs.add(new Attribute("c3"));
		
		Project project = new Project(bc, attrs);
		
		Join join = new Join(a, project, new Predicate(new Attribute("a1"), new Attribute("b1")));
		
		Select select = new Select(join, new Predicate(new Attribute("a1"), new Attribute("c2")));
		
		Inspector inspector = new Inspector();
		Estimator estimator = new Estimator();
		
		select.accept(estimator);
		select.accept(inspector);
		
		assert(select.getOutput().getTupleCount() == 200);
		assert(select.getOutput().getAttribute(new Attribute("a1")).getValueCount() == select.getOutput().getAttribute(new Attribute("c2")).getValueCount());
		assert(select.getOutput().getAttribute(new Attribute("a1")).getValueCount() == 13);
	}

}
