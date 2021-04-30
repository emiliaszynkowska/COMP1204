package sjdb;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Optimises an operator plan
 * @author Emilia Szynkowska
 * @author eas1g18@soton.ac.uk
 */
public class Optimiser {
    private Catalogue catalogue;
    private Estimator estimator;
    private int totalCost;
    private ArrayList<Scan> allScans = new ArrayList<>();
    private ArrayList<Attribute> allAttributes = new ArrayList<>();
    private ArrayList<Predicate> allPredicates = new ArrayList<>();

    public Optimiser(Catalogue cat) {
        catalogue = cat;
        estimator = new Estimator();
        totalCost = 0;
    }

    /**
     * Optimises an operator plan to minimise its total cost:
     * Finds all attributes, predicates, and scans
     * Pushes down select and project operators
     * Orders product and join operators
     * Returns the optimised plan with the lowest cost
     * @param plan the original plan
     * @return the optimised plan
     */
    public Operator optimise(Operator plan) {
        // Find all attributes, predicates, and scans
        // Push down select and project operators
        // Order product and join operators
        findAll(plan);
        ArrayList<Operator> selectsProjects = pushSelectsProjects(plan);
        Operator productsJoins = orderProductsJoins(selectsProjects, plan);
        totalCost = 0; System.out.println("\nOLD PLAN " + plan.toString() + "\nOLD COST " + getCost(plan));
        totalCost = 0; System.out.println("\nNEW PLAN " + productsJoins.toString() + "\nNEW COST " + getCost(productsJoins));
        return productsJoins;
    }

    /**
     * Pushes down select and project operators:
     * Iterates through all scan operators
     * Builds select operators from the plan
     * Builds projects operators from the select operators
     * Returns the new select and project operators
     * @param plan the current plan
     * @return the set of select and project operators
     */
    public ArrayList<Operator> pushSelectsProjects(Operator plan) {
        ArrayList<Operator> selectsProjects = new ArrayList<>();

        // Build select and project operators
        for (Scan scan : allScans) {
            Operator selects = buildSelects(scan);
            Operator projects = buildProjects(selects, findAttributes(allPredicates, plan));
            if (!selectsProjects.contains(projects))
                selectsProjects.add(projects);
        }

        return selectsProjects;
    }

    /**
     * Builds new select operators:
     * Iterates through all predicates
     * Finds predicates containing matching attributes
     * Builds select operators from each predicate
     * Removes used predicates
     * Returns the new plan with select operators
     * @param plan the current plan
     * @return the new plan with select operators
     */
    public Operator buildSelects(Operator plan) {
        ArrayList<Attribute> attributes = (ArrayList) plan.getOutput().getAttributes();
        ArrayList<Predicate> predicates = new ArrayList<>();

        // Find all predicates where attr=attr or attr=value
        // Create a new select operator
        for (Predicate predicate : allPredicates) {
            if ((predicate.equalsValue() && (attributes.contains(predicate.getLeftAttribute())))
                    | (!predicate.equalsValue() && attributes.contains(predicate.getLeftAttribute())
                    && attributes.contains(predicate.getRightAttribute()))) {
                plan = new Select(plan, predicate);
                plan.accept(estimator);
                if (!predicates.contains(predicate))
                    predicates.add(predicate);
            }
        }

        allPredicates.removeAll(predicates);

        return plan;
    }

    /**
     * Builds new project operators:
     * Finds attributes in the plan
     * Creates a new project operator with these attributes
     * Returns the new plan with project operators
     * @param plan the current plan
     * @param attributes attributes found in the plan
     * @return the new plan with project operators
     */
    public Operator buildProjects(Operator plan, ArrayList<Attribute> attributes) {
        plan.accept(estimator);

        // Find all attributes in the plan operator
        attributes.retainAll(plan.getOutput().getAttributes());
        return plan;

        // Create a project operator
//        if (!attributes.isEmpty()) {
//            Project project = new Project(plan, attributes);
//            project.accept(estimator);
//            return project;
//        }
//        else
//            return plan;
    }

    /**
     * Orders product and join operators:
     * Generates permutations of predicates
     * Iterates through all permutations
     * Creates a new plan and calculates the cost for each permutation
     * Returns the plan with the lowest cost
     * @param selectsProjects the new select and project operators
     * @param plan the current plan
     * @return the new plan with product and join operators
     */
    public Operator orderProductsJoins(ArrayList<Operator> selectsProjects, Operator plan) {
        // Find all permutations of predicates
        ArrayList<ArrayList<Predicate>> permutations = new ArrayList<>();
        permutations.add(new ArrayList<>());
        permute(0, allPredicates, permutations);

        if (permutations.isEmpty()) {
            List<List<Predicate>> result = new ArrayList<>();
            result.add(new ArrayList<Predicate>());
        }

        Operator productsJoins = null;
        int bestCost = Integer.MAX_VALUE;

        // Find the permutation with the lowest cost
        for (ArrayList<Predicate> permutation : permutations) {
            ArrayList<Operator> operators = new ArrayList<>(selectsProjects);
            Operator operator = buildProductJoin(operators, permutation, plan);
            totalCost = 0;
            int cost = getCost(operator);
            if (cost < bestCost) {
                productsJoins = operator;
                bestCost = cost;
            }
        }

        return productsJoins;
    }

    /**
     * Generates permutations from a list of predicates:
     * Iterates through the predicates recursively
     * Creates permutations by repeatedly swapping predicates
     * Adds new permutations to the permutation list
     * @param n current position in the list
     * @param predicates the list of predicates to permute
     * @param permutations the list of permutations to add to
     */
    public void permute(int n, ArrayList<Predicate> predicates, ArrayList<ArrayList<Predicate>> permutations) {
        // Find all permutations of the predicates
        for (int i=n; i<predicates.size(); i++) {
            ArrayList<Predicate> permutation = new ArrayList<>(predicates);
            Collections.swap(permutation, i, n);
            permute(n + 1, permutation, permutations);
            Collections.swap(permutation, n, i);
            if (n == predicates.size() - 1 & !permutations.contains(permutation))
                permutations.add(permutation);
        }
    }

    /**
     * Builds new product and join operators:
     * Iterates through predicates in the permutation
     * Finds attributes in the predicate
     * If one attribute is found i.e. attr=val, creates a new select operator
     * If two attributes are found i.e. attr=attr, creates a new join operator
     * If further attributes are found, create a new project operator
     * Remove used predicates
     * Iterate through selectsProjects
     * For any remaining operators, create new product operators
     * Return the new plan with products and joins
     * @param selectsProjects the new select and project operators
     * @param permutation the current permuation of predicates
     * @param plan the current plan
     * @return
     */
    public Operator buildProductJoin(ArrayList<Operator> selectsProjects, ArrayList<Predicate> permutation, Operator plan) {
        // Create the output operator
        Operator out = null;
        // If there is only one operator
        if (selectsProjects.size() == 1) {
            out = selectsProjects.get(0);
            out.accept(estimator);
            return out;
        }

        Iterator<Predicate> iterator = permutation.iterator();
        Operator left = null, right = null;

        // Find the predicate attributes
        while (iterator.hasNext()) {
            Predicate predicate = iterator.next();

            // Find the predicate attributes
            for (Operator operator : selectsProjects) {
                if (operator.getOutput().getAttributes().contains(predicate.getLeftAttribute()))
                    left = operator;
                if (operator.getOutput().getAttributes().contains(predicate.getRightAttribute()))
                    right = operator;
            }

            selectsProjects.remove(left);
            selectsProjects.remove(right);

            // Create a join operator
            if (left != null && right != null) {
                out = new Join(left, right, predicate);
                iterator.remove();
            }
            // Create a select operator
            else if (left != null && right == null) {
                out = new Select(left, predicate);
                iterator.remove();
            }
            else if (right != null) {
                out = new Select(right, predicate);
                iterator.remove();
            }

            out.accept(estimator);
            ArrayList<Attribute> planAttributes = findAttributes(permutation, plan);
            ArrayList<Attribute> outAttributes = (ArrayList) out.getOutput().getAttributes().stream().distinct().collect(Collectors.toList());

            // If all attributes are found, add the output to the operators
            if (planAttributes.size() == outAttributes.size() && outAttributes.containsAll(planAttributes) && !selectsProjects.contains(out)) {
                selectsProjects.add(out);
            }
            else {
                // Find all attributes in the plan and output
                outAttributes.retainAll(planAttributes);
                // If no attributes are found, add the output to operators
                if (outAttributes.isEmpty() && !selectsProjects.contains(out))
                        selectsProjects.add(out);
                // Otherwise create a project operator
                else {
                    Project project = new Project(out, outAttributes);
                    project.accept(estimator);
                    if (!selectsProjects.contains(project))
                        selectsProjects.add(project);
                }
            }
        }

        // Create product
        Iterator<Operator> productIterator = selectsProjects.iterator();
        right = null;

        while (selectsProjects.size() > 1) {
            for (int i=0; i<selectsProjects.size()-1; i++) {
                left = selectsProjects.get(i);
                right = selectsProjects.get(i+1);
                selectsProjects.remove(i);
                selectsProjects.remove(i);
                Product product = new Product(left, right);
                product.accept(estimator);
                if (!selectsProjects.contains(product))
                    selectsProjects.add(product);
            }
        }

        return selectsProjects.get(0);
    }

    /**
     * Finds attributes in a given plan:
     * Iterates through attributes in predicates
     * Iterates through attributes in project operators
     * Returns all found attributes
     * @param predicates the predicates to search
     * @param plan the current plan
     * @return attributes found in the plan
     */
    public ArrayList<Attribute> findAttributes(ArrayList<Predicate> predicates, Operator plan) {
        ArrayList<Attribute> attributes = new ArrayList<>();

        // Find attributes in predicates
        for (Predicate predicate : predicates) {
            Attribute left = predicate.getLeftAttribute();
            Attribute right = predicate.getRightAttribute();
            if (!attributes.contains(left))
                attributes.add(left);
            if (!attributes.contains(right) && right != null)
                attributes.add(right);
        }

        // Find attributes in project operators
        if (plan instanceof Project) {
            for (Attribute attribute : ((Project) plan).getAttributes()) {
                if (!attributes.contains(attribute))
                    attributes.add(attribute);
            }
        }

        return attributes;
    }

    /**
     * Finds attributes, predicates, and scans in a given plan:
     * Travels through the plan recursively
     * Finds attributes in project operators
     * Finds attributes and predicates in select operators
     * Finds scan operators
     * Adds new attributes, predicates, and scans to allAttributes, allPredicates, and allScans
     * @param plan the current plan
     */
    public void findAll(Operator plan) {
        // Project
        if (plan instanceof Project) {
            // Add to allAttributes
            for (Attribute attribute : ((Project) plan).getAttributes()) {
                if (!allAttributes.contains(attribute))
                    allAttributes.add(attribute);
            }
            findAll(((Project) plan).getInput());
        }

        // Select
        else if (plan instanceof Select) {
            // Add to allPredicates
            Predicate predicate = ((Select) plan).getPredicate();
            if (!allPredicates.contains(predicate))
                allPredicates.add(predicate);
            // Add to allAttributes
            Attribute left = ((Select) plan).getPredicate().getLeftAttribute();
            Attribute right = ((Select) plan).getPredicate().getRightAttribute();
            if (!allAttributes.contains(left))
                allAttributes.add(left);
            if (!allAttributes.contains(right) & right != null)
                allAttributes.add(right);
            findAll(((Select) plan).getInput());
        }

        // Product
        else if (plan instanceof Product) {
            findAll(((Product) plan).getLeft());
            findAll(((Product) plan).getRight());
        }

        // Join
        else if (plan instanceof Join) {
            findAll(((Join) plan).getLeft());
            findAll(((Join) plan).getRight());
        }

        // Scan
        else if (plan instanceof Scan) {
            // Add to allScans
            Scan scan = new Scan((NamedRelation) ((Scan) plan).getRelation());
            if (!allScans.contains(scan))
                allScans.add(scan);
        }
    }

    /**
     * Calculates the cost of a given plan:
     * Travels through the plan recursively
     * Uses the estimator to visit each operator
     * Adds the cost of each operator to the total cost
     * Returns the total cost
     * @param plan the current plan
     * @return the total cost of the plan
     */
    public int getCost(Operator plan) {

        // Project
        if (plan instanceof Project) {
            // Add the cost of this operator
            estimator.visit((Project) plan);
            totalCost += plan.getOutput().getTupleCount();
            // Go to the inner operator
            getCost(((Project) plan).getInput());
        }

        // Select
        else if (plan instanceof Select) {
            // Add the cost of this operator
            estimator.visit((Select) plan);
            totalCost += plan.getOutput().getTupleCount();
            // Go to the inner operator
            getCost(((Select) plan).getInput());
        }

        // Product
        else if (plan instanceof Product) {
            // Add the cost of this operator
            estimator.visit((Product) plan);
            totalCost += plan.getOutput().getTupleCount();
            // Go to the inner operators
            getCost(((Product) plan).getLeft());
            getCost(((Product) plan).getRight());
        }

        // Join
        else if (plan instanceof Join) {
            // Add the cost of this operator
            estimator.visit((Join) plan);
            totalCost += plan.getOutput().getTupleCount();
            // Go to the inner operators
            getCost(((Join) plan).getLeft());
            getCost(((Join) plan).getRight());
        }

        // Scan
        else if (plan instanceof Scan) {
            // Add the cost of this operator
            estimator.visit((Scan) plan);
            totalCost += plan.getOutput().getTupleCount();
        }

        return totalCost;
    }

}
