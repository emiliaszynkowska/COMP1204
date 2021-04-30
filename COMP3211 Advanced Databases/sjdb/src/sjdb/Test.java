package sjdb;

import java.io.*;
import java.util.ArrayList;
import sjdb.DatabaseException;

public class Test {
	private Catalogue catalogue;
	
	public Test() {
	}

	public static void main(String[] args) throws Exception {
		Catalogue catalogue = createCatalogue();
		Inspector inspector = new Inspector();
		Estimator estimator = new Estimator();

		Operator plan = query(catalogue);
		plan.accept(estimator);
		plan.accept(inspector);

		Optimiser optimiser = new Optimiser(catalogue);
		Operator planopt = optimiser.optimise(plan);
		planopt.accept(estimator);
		planopt.accept(inspector);
	}
	
	public static Catalogue createCatalogue() {
		Catalogue cat = new Catalogue();
		cat.createRelation("Person", 400);
		cat.createAttribute("Person", "persid", 350);
		cat.createAttribute("Person", "persname", 47);
		cat.createAttribute("Person", "age", 47);
		cat.createRelation("Project", 40);
		cat.createAttribute("Project", "projid", 40);
		cat.createAttribute("Project", "projname", 35);
		cat.createAttribute("Project", "dept", 5);
		cat.createRelation("Department", 5);
		cat.createAttribute("Department", "deptid", 5);
		cat.createAttribute("Department", "deptname", 5);
		cat.createAttribute("Department", "manager", 5);
		
		return cat;
	}

	public static Operator query(Catalogue cat) throws Exception {
		Scan person = new Scan(cat.getRelation("Person"));
		Scan department = new Scan(cat.getRelation("Department"));
		Scan project= new Scan(cat.getRelation("Project"));

		// Query 1
		Select s1 = new Select(department, new Predicate(new Attribute("deptname"), "Research"));

		ArrayList<Attribute> atts1 = new ArrayList<>();
		atts1.add(new Attribute("projid"));
		atts1.add(new Attribute("dept"));

		Project p1 = new Project(project, atts1);

		Join j1 = new Join(p1, s1, new Predicate(new Attribute("dept"), new Attribute("deptid")));

		ArrayList<Attribute> atts2 = new ArrayList<>();
		atts2.add(new Attribute("projid"));

		Project plan = new Project(j1, atts2);

		return plan;
	}
	
}

