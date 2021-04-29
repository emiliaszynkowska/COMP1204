package sjdb;

import java.util.*;

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

    public Operator optimise(Operator plan) {
        visit(plan);
        ArrayList<Operator> operators = pushSelectsProjects(plan);
        Operator optPlan = orderProductsJoins(operators, plan);
        totalCost = 0; System.out.println("\nOLD PLAN " + plan.toString() + "\nOLD COST " + getCost(plan));
        totalCost = 0; System.out.println("\nNEW PLAN " + optPlan.toString() + "\nNEW COST " + getCost(optPlan));
        return optPlan;
    }

    public ArrayList<Operator> pushSelectsProjects(Operator plan) {
        ArrayList<Operator> operators = new ArrayList<>();

        for (Scan scan : allScans) {
            Operator selects = buildSelects(scan);
            Operator projects = buildProjects(selects, getAttributes(allPredicates, plan));
            if (!operators.contains(projects))
                operators.add(projects);
        }

        return operators;
    }

    public Operator orderProductsJoins(ArrayList<Operator> operators, Operator plan) {
        ArrayList<ArrayList<Predicate>> permutations = new ArrayList<>();
        permute(0, allPredicates, permutations);

        Operator optPlan = null;
        int minCost = Integer.MAX_VALUE;

        for (ArrayList<Predicate> permutation : permutations) {
            Operator operator = buildProductJoin(operators, permutation, plan);
            totalCost = 0;
            int cost = getCost(plan);
            if (cost < minCost) {
                optPlan = operator;
                minCost = cost;
            }
        }

        return optPlan;
    }

    public ArrayList<Attribute> getAttributes(ArrayList<Predicate> predicates, Operator plan) {
        ArrayList<Attribute> attributes = new ArrayList<>();

        for (Predicate predicate : predicates) {
            Attribute left = predicate.getLeftAttribute();
            Attribute right = predicate.getRightAttribute();
            if (!attributes.contains(left))
                attributes.add(left);
            if (!attributes.contains(right) & right != null)
                attributes.add(right);
        }

        if (plan instanceof Project) {
            for (Attribute attribute : ((Project) plan).getAttributes()) {
                if (!attributes.contains(attribute))
                    attributes.add(attribute);
            }
        }

        return attributes;
    }

    public Operator buildSelects(Operator plan) {
        ArrayList<Attribute> attributes = (ArrayList) plan.getOutput().getAttributes();

        for (Predicate predicate : allPredicates) {
            if ((predicate.equalsValue() & (attributes.contains(predicate.getLeftAttribute())))
                    | (!predicate.equalsValue() & attributes.contains(predicate.getLeftAttribute())
                    & attributes.contains(predicate.getRightAttribute()))) {
                plan = new Select(plan, predicate);
                plan.accept(estimator);
            }
        }

        return plan;
    }

    public Operator buildProjects(Operator plan, ArrayList<Attribute> attributes) {
        plan.accept(estimator);
        ArrayList<Attribute> projectAttributes = new ArrayList<>(attributes);
        projectAttributes.retainAll(plan.getOutput().getAttributes());

        if (!projectAttributes.isEmpty()) {
            Project project = new Project(plan, projectAttributes);
            project.accept(estimator);
            return project;
        }
        else
            return plan;
    }

    public Operator buildProductJoin(ArrayList<Operator> operators, ArrayList<Predicate> predicates, Operator plan) {
        Operator out = null;

        if (operators.size() == 1)
            out = operators.get(0);

        Operator left = null;
        Operator right = null;
        Iterator<Predicate> iterator = predicates.iterator();

        while (iterator.hasNext()) {
            Predicate predicate = iterator.next();

            for (Operator operator : operators) {
                if (operator.getOutput().getAttributes().contains(predicate.getLeftAttribute()))
                    left = operator;
                else if (operator.getOutput().getAttributes().contains(predicate.getRightAttribute()))
                    right = operator;
            }

            if (left != null & right != null) {
                out = new Join(left, right, predicate);
                iterator.remove();
            }
            else if (left != null) {
                out = new Select(left, predicate);
                iterator.remove();
            }
            else if (right != null) {
                out = new Select(right, predicate);
                iterator.remove();
            }

            out.accept(estimator);
            ArrayList<Attribute> planAttributes = (ArrayList) getAttributes(predicates, plan);
            ArrayList<Attribute> outAttributes = (ArrayList) out.getOutput().getAttributes();
            ArrayList<Attribute> totalAttributes = new ArrayList<>();

            if (planAttributes.size() == outAttributes.size() & outAttributes.containsAll(planAttributes) & !operators.contains(out)) {
                operators.add(out);
            }
            else {
                totalAttributes = outAttributes;
                totalAttributes.retainAll(planAttributes);
                if (totalAttributes.isEmpty())
                    if (!operators.contains(out))
                        operators.add(out);
                else {
                    Project project = new Project(out, totalAttributes);
                    project.accept(estimator);
                    if (!operators.contains(project))
                        operators.add(project);
                }
            }
        }

        while (operators.size() > 1) {
            Product product = new Product(operators.get(0), operators.get(1));
            product.accept(estimator);
            if (!operators.contains(product))
                operators.add(product);
            operators.remove(1);
            operators.remove(0);
        }

        return operators.get(0);
    }

    public void permute(int n, ArrayList<Predicate> predicates, ArrayList<ArrayList<Predicate>> permutations) {
        for (int i=n; i<predicates.size(); i++) {
            ArrayList<Predicate> permutation = new ArrayList<>();
            permutation.addAll(predicates);
            Collections.swap(permutation, i, n);
            permute(n + 1, permutation, permutations);
            Collections.swap(permutation, n, i);
            if (n == predicates.size() - 1 & !permutations.contains(permutation))
                permutations.add(permutation);
        }
    }

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

    public void visit(Operator plan) {
        // Project
        if (plan instanceof Project) {
            // Add to allAttributes
            for (Attribute attribute : ((Project) plan).getAttributes()) {
                if (!allAttributes.contains(attribute))
                    allAttributes.add(attribute);
            }
            visit(((Project) plan).getInput());
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
            visit(((Select) plan).getInput());
        }

        // Product
        else if (plan instanceof Product) {
            visit(((Product) plan).getLeft());
            visit(((Product) plan).getRight());
        }

        // Join
        else if (plan instanceof Join) {
            visit(((Join) plan).getLeft());
            visit(((Join) plan).getRight());
        }

        // Scan
        else if (plan instanceof Scan) {
            // Add to allScans
            Scan scan = new Scan((NamedRelation) ((Scan) plan).getRelation());
            if (!allScans.contains(scan))
                allScans.add(scan);
        }
    }

}
