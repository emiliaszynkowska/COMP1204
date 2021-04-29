package sjdb;

import java.util.*;

/**
 * Estimates the cost of an operator
 * @author Emilia Szynkowska
 * @author eas1g18@soton.ac.uk
 */

public class Estimator implements PlanVisitor {

	/**
	 * Estimates the cost of a scan operator:
	 * Creates an output relation using the tuple count of the input relation
	 * Iterates through the attributes in the input relation
	 * Adds attributes to the output relation
	 * @param op Scan operator to be visited
	 */
	// Scan = T(R)
	public void visit(Scan op) {
		Relation in = op.getRelation();
		Relation out = new Relation(in.getTupleCount());
		
		Iterator<Attribute> iter = in.getAttributes().iterator();
		while (iter.hasNext()) {
			out.addAttribute(new Attribute(iter.next()));
		}

		// System.out.println("SCAN " + out.render());
		op.setOutput(out);
	}

	/**
	 * Estimates the cost of a project operator:
	 * Creates an output relation using the tuple count of the input relation
	 * Iterates through attributes in the operator
	 * Adds matching attributes and value counts from the operator and input relation
	 * @param op Project operator to be visited
	 */
	public void visit(Project op) {
		// Find the input
		Relation in = op.getInput().getOutput();
		// Set the output relation
		// Number of tuples = T(R)
		Relation out = new Relation(in.getTupleCount());

		// Find matching attributes in the input and output
		for (Attribute a : op.getAttributes()) {
			for (Attribute b : in.getAttributes()) {
				if (a.equals(b))
					out.addAttribute(new Attribute(b.getName(), b.getValueCount()));
			}
		}

		// System.out.println("PROJECT " + out.render());
		op.setOutput(out);
	}

	/**
	 * Estimates the cost of a select operator:
	 * Finds the left and right attributes in the predicate
	 * If the predicate is of the form attr=val
	 	* Creates an output relation using T(R)/V(R,A)
	 	* Where T(R) is the tuple count of the input relation
	 	* Where V(R,A) is the number of distinct values for the attribute A in relation R
	 	* Adds attributes and value counts
	 * If the predicate is of the form attr=attr
	 	* Creates an output relation using T(R)/max(V(R,A),V(R,B))
	 	* Where T(R) is the tuple count of the input relation
	 	* Where V(R,A) is the number of distinct values for the attribute A in relation R
	 	* Where V(R,B) is the number of distinct values for the attribute B in relation R
	 	* Adds attributes and value counts using min(V(R,A),V(R,B)
	 * @param op Select operator to be visited
	 */
	public void visit(Select op) {
		// Find the input
		Relation in = op.getInput().getOutput();
		// Create an output relation
		Relation out;

		// Find the left attribute
		Predicate pred = op.getPredicate();
		Attribute left = new Attribute(pred.getLeftAttribute().getName());

		// Find the left attribute in the input
		for (Attribute a : in.getAttributes()) {
			if (a.equals(left))
				left = new Attribute(a.getName(), a.getValueCount());
		}

		// For predicates of the form attr=val
		if (op.getPredicate().equalsValue()) {

			// Set the output
			// Number of tuples = T(R)/V(R,A)
			double v = (double) in.getTupleCount() / (double) left.getValueCount();
			out = new Relation((int) Math.ceil(v));

			for (Attribute a : in.getAttributes()) {
				if (a.equals(left))
					out.addAttribute(new Attribute(a.getName(), 1));
				else
					out.addAttribute(new Attribute(a.getName(), a.getValueCount()));
			}
		}

		// For predicates of the form attr=attr
		else {
			// Find the right attribute
			Attribute right = new Attribute(pred.getRightAttribute().getName());

			// Find the right attribute in the input
			for (Attribute a : in.getAttributes()) {
				if (a.equals(right))
					right = new Attribute(a.getName(), a.getValueCount());
			}

			// Set the output relation
			// Number of tuples = T(R)/max(V(R,A),V(R,B))
			out = new Relation(in.getTupleCount()/Math.max(left.getValueCount(), right.getValueCount()));

			// Number of values = min(V(R,A),V(R,B))
			int v = Math.min(left.getValueCount(), right.getValueCount());

			for (Attribute b : in.getAttributes()) {
				out.addAttribute(new Attribute(b.getName(), v));
			}
		}

		// System.out.println("SELECT " + out.render());
		op.setOutput(out);
	}

	/**
	 * Estimates the cost of a product operator
	 * Finds the left and right relations
	 * Creates an output relation using the tuple counts of the left and right relations
	 * Adds attributes and tuple counts from the left and right relations
	 * @param op Product operator to be visited
	 */
	public void visit(Product op) {
		// Find the left and right outputs
		Relation left = op.getLeft().getOutput();
		Relation right = op.getRight().getOutput();

		// Set the output relation
		// Number of tuples = T(R)*T(S)
		Relation out = new Relation(left.getTupleCount() * right.getTupleCount());

		// Add attributes from the left relation
		for (Attribute a : left.getAttributes()) {
			out.addAttribute(new Attribute(a.getName(), a.getValueCount()));
		}

		// Add attributes from the right relation
		for (Attribute a : right.getAttributes()) {
			out.addAttribute(new Attribute(a.getName(), a.getValueCount()));
		}

		// System.out.println("PRODUCT " + out.render());
		op.setOutput(out);
	}

	/**
	 * Estimates the cost of a join operator
	 * Finds the left and right relations
	 * Finds attributes in the predicates
	 * Creates an output relation using T(R)*T(S)/max(V(R,A),V(S,B))
	 	* Where T(R) is the tuple count of the left relation
	 	* Where T(S) is the tuple count of the right relation
	 	* Where V(R,A) is the number of distinct values for the attribute A in relation R
	 	* Where V(S,B) is the number of distinct values for the attribute B in relation S
	 * Adds attributes and value counts using min(V(R,A),V(S,B))
	 * @param op
	 */
	public void visit(Join op) {
		// Find the left and right outputs
		Relation left = op.getLeft().getOutput();
		Relation right = op.getRight().getOutput();

		// Find the attributes
		Attribute a = op.getPredicate().getLeftAttribute();
		Attribute b = op.getPredicate().getRightAttribute();

		// Set the attributes
		for (Attribute c : left.getAttributes()) {
			if (c.equals(a))
				a = new Attribute(c.getName(), c.getValueCount());
		}
		for (Attribute d : right.getAttributes()) {
			if (d.equals(b))
				b = new Attribute(d.getName(), d.getValueCount());
		}

		// Set the output relation
		// Number of tuples = T(R)*T(S)/max(V(R,A),V(S,B))
		Relation out = new Relation((left.getTupleCount() * right.getTupleCount())/Math.max(a.getValueCount(),b.getValueCount()));

		// Number of values = min(V(R,A),V(S,B))
		int v = Math.min(a.getValueCount(), b.getValueCount());

		for (Attribute e : left.getAttributes()) {
			out.addAttribute(new Attribute(e.getName(), v));

		}
		for (Attribute f : right.getAttributes()) {
			if (f.equals(left) | f.equals(right))
				out.addAttribute(new Attribute(f.getName(), v));
			else
				out.addAttribute(new Attribute(f.getName(), f.getValueCount()));
		}

		// System.out.println("JOIN " + out.render());
		op.setOutput(out);
	}

}
