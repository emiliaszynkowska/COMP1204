package sjdb;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class will optimise an Operator plan
 * @author shakib-bin hamid
 */
public class Optimiser2 implements PlanVisitor {

    @SuppressWarnings("unused")
    private int totalCost;
    private Catalogue cat; // taken care of in the estimator
    private static final Estimator EST = new Estimator(); // the Estimator in Use here

    public Optimiser2(Catalogue cat) {
        this.cat = cat;
    }

    public Operator optimise(Operator plan) {

        //SectionExtractor extractor = new SectionExtractor();
        plan.accept(this);

        // push down the SELECTs and PROJECTs to the Scan leaves on the canonical plan
        List<Operator> operationBlocks = pushSelectsAndProjectsDownForScans(allScans, allAttributes, allPredicates, plan);

        // then from those blocks, exhaust the SELECTS by creating JOINs,
        // putting extra SELECTs and remove unnecessary ATTRIBUTES by adding PROJECTs as we go along and
        // finally make PRODUCTs if necessary, once all that could be selected or projected or joined
        // reorder for each permutation of predicate order
        Operator optimisedPlan = createBESTOrderOfJoinOrProducts(allPredicates, operationBlocks, plan);

        return optimisedPlan;
    }

    /**
     * Create a permutation of the PREDICATEs Set, then
     * Make a JOIN ordering for each, ESTimate the cost and
     * Select the CHEAPEST JOIN ordering
     *
     * @param originalPreds the Set of PREDICATEs to Permute (Will get turned to List inside)
     * @param ops the List of Operators to build the tree out of
     * @param root the root of the tree
     * @return the Operator with the best of everything
     */
    private Operator createBESTOrderOfJoinOrProducts(Set<Predicate> originalPreds, List<Operator> ops, Operator root){

        // The list of PREDICATEs - necessary to permute on List, Set does not permute
        List<Predicate> preds = new ArrayList<>();
        preds.addAll(originalPreds);

        // Permuations of PREDICATEs
        List<List<Predicate>> permutedPreds = generatePerm(preds);

        // CheapEST plan found so far
        Operator cheapESTPlan = null;
        Integer cheapESTCost = Integer.MAX_VALUE;

        // Iterate for each permutation
        for (List<Predicate> p : permutedPreds) {

            // create a fresh set of operators
            List<Operator> tempOps = new ArrayList<>();
            tempOps.addAll(ops);

            // make a tree
            Operator aPlan = buildProductOrJoin(tempOps, p, root);

            Integer i = getCost(aPlan);
            System.out.println("Found plan with cost: " + i);

            // make the cheapEST plan
            cheapESTPlan = (i < cheapESTCost) ? aPlan : cheapESTPlan;
            cheapESTCost = (i < cheapESTCost) ? i : cheapESTCost;
        }

        return cheapESTPlan;
    }

    /**
     * for each scan or relations at the leaves, process it as much as possible.
     * the output operator should be
     * SCAN => [SELECT] x n => [PROJECT]_neededAttrs
     *
     * @param scans the SCAN operators
     * @param attrs the COMPLETE set of ATTRIBUTES needed from the scans
     * @param predicates the COMPLETE set of PREDICATES needed from the scans
     * @param root
     * @return the List of Operator BLOCKS in (SCAN => [SELECT] x n => [PROJECT]_neededAttrs) form,
     * 			predicates will be mutated and truncated by removing the used ones
     */
    private List<Operator> pushSelectsAndProjectsDownForScans(Set<Scan> scans, Set<Attribute> attrs, Set<Predicate> predicates, Operator root) {

        // the block of resultant operators from each of the SCANs
        List<Operator> operatorBlocks = new ArrayList<>(scans.size());

        for (Scan s: scans){
            // to SCAN => [SELECT] x n
            Operator o = buildSelectsOnTop(s, predicates);
            List<Predicate> temp = new ArrayList<>();
            temp.addAll(predicates);
            getNecessaryAttrs(temp, root);
            // [SCAN] || [SELECT] x n => [PROJECT]
            operatorBlocks.add(buildProjectOnTop(o, getNecessaryAttrs(temp, root)));
        }

        return operatorBlocks;
    }

    /**
     * Iterate over the preds to see if any be applied on root of op.
     * Then builds [multiple] SELECT Operators on root of op and returnds the rootmost one.
     *
     * @param op Operator to build SELECT operators on root of
     * @param preds the set of PREDICATEs to choose from
     * @return an Operator that is in the form of (op => [SELECT] x n) and
     * 			the Set of PREDICATES is mutated and truncated by removing the used ones
     */
    private Operator buildSelectsOnTop(Operator op, Set<Predicate> preds){

        // The result
        Operator result = op;
        // the attributes available at this point, building SELECT doesn't remove any attributes
        List<Attribute> availableAttrs = result.getOutput().getAttributes();

        // Iterate over the PREDICATEs to see if any are applicable to the latEST Operator in the list
        Iterator<Predicate> it = preds.iterator();
        while(it.hasNext()){

            Predicate currentPred = it.next();

            // If output of the latEST Operator isn't set, set it
            if(result.getOutput() == null) result.accept(EST);

            // attr = val and the ATTRIBUTE comes from the Operator's output relation
            if ((currentPred.equalsValue() && availableAttrs.contains(currentPred.getLeftAttribute())) ||
                    (!currentPred.equalsValue() && availableAttrs.contains(currentPred.getLeftAttribute()) && availableAttrs.contains(currentPred.getRightAttribute())))
            {
                // add a new SELECT operator on root, note how the output isn't set
                result = new Select(result, currentPred);
                // remove the PREDICATE because it's been dealt with
                it.remove();
            }

        }

        return result;
    }

    /**
     * Goes through the Set of needed ATTRIBUTEs and
     * checks which one of the current ATTRIBUTEs are needed.
     *
     * If not ALL ATTRIBUTEs are needed, then puts a PROJECT on root.
     *
     * @param op the Operator to build the project on Top of
     * @param attrs the Set of ATTRIBUTES to check for
     * @return an Operator in the form of [op] || [PROJECT => op]
     */
    private Operator buildProjectOnTop(Operator op, Set<Attribute> attrs){

        // if op doesn't have an output, fix that
        if(op.getOutput() == null) op.accept(EST);

        // see which attributes are to be projected
        List<Attribute> attrsToProjectFromOp = new ArrayList<>(attrs);
        attrsToProjectFromOp.retainAll(op.getOutput().getAttributes());

        // not all attributes from op is necessary
        if (attrsToProjectFromOp.size() > 0) {
            Operator op2 = new Project(op, attrsToProjectFromOp);
            op2.accept(EST);
            return op2;
        } else {
            return op;
        }
    }
    /**
     * Go through each of the PREDICATEs and
     * see if any can be applied to any ONE/PAIR of Operators.
     *
     * If no Operator found for a PREDICATE, make a PRODUCT,
     * If ONE Operator found for a PREDICATE, make a SELECT,
     * If TWO Operators found for a PREDICATE, make a JOIN
     *
     * Also, if at any point, some ATTRIBUTE is not necessary, it is PROJECTed OUT.
     *
     * Finally one Operator will be returned with all such operations performed
     *
     * @param ops the List of Operators to process into PRODUCTs, JOINs or SELECTs
     * @param preds The List of Predicates to check through
     * @param root the root of the tree
     * @return One Operator where all the operations are performed in form
     * 			[JOIN] x n <=> [SELECT] x n <=> [TIMES] x n <=> [PROJECT] x n
     */
    private Operator buildProductOrJoin(List<Operator> ops, List<Predicate> preds, Operator root){

        Operator result = null;

        if (ops.size() == 1){
            result = ops.get(0);
            if (result.getOutput() == null) result.accept(EST);
            return result;
        }

        // First Iterate over the PREDICATEs and
        // EXHAUST it until they've been made into JOINs or SELECTs
        Iterator<Predicate> it = preds.iterator();
        while(it.hasNext()){

            Predicate currentPred = it.next();
            // The potential Operator with the left ATTRIBUTE in its output Relation
            Operator left = extractOperatorForAttribute(ops, currentPred.getLeftAttribute());
            // The potential Operator with the left ATTRIBUTE in its output Relation
            Operator right = extractOperatorForAttribute(ops, currentPred.getRightAttribute());

            // if only ONE potential Operators found for the PREDICATE, create a SELECT
            if((left == null && right != null) || (right == null && left != null)){
                result = new Select(left != null? left : right, currentPred);
                it.remove();
            }

            // if BOTH potential Operators found for the PREDICATE, create a JOIN
            if(left != null && right != null){
                result = new Join(left, right, currentPred);
                it.remove();
            }

            // if the output Relation hasn't been sorted yet
            if (result.getOutput() == null) result.accept(EST);

            // get the ATTRIBUTEs still needed based on the remaining predicates and the very rootmost root
            Set<Attribute> neededAttrs = getNecessaryAttrs(preds, root);

            // Now if NOT ALL ATTRIBUTEs available now are necessary, PROJECT some OUT
            List<Attribute> availableAttrs = result.getOutput().getAttributes();

            // if no ATTRIBUTE can be left out
            if (neededAttrs.size() == availableAttrs.size() && availableAttrs.containsAll(neededAttrs)){
                ops.add(result);
            }else{
                // the ATTRIBUTE needed to be kept
                List<Attribute> attrsToKeep = availableAttrs.stream().filter(attr -> neededAttrs.contains(attr)).collect(Collectors.toList());

                if (attrsToKeep.size() == 0) {
                    ops.add(result); // Otherwise it's vacuously true and causes a BUG
                } else {
                    Project tempProj = new Project(result, attrsToKeep);
                    tempProj.accept(EST); // set the output Relation right
                    ops.add(tempProj);
                }
            }
        }

        // Now if multiple Operators have been made, turn them into PRODUCTs, because no PREDICATEs are left
        while(ops.size() > 1) {
            // Get the first two
            Operator b1 = ops.get(0);
            Operator b2 = ops.get(1);
            Operator product = new Product(b1, b2);
            product.accept(EST);

            // remove the first two and add the new one
            ops.remove(b1);
            ops.remove(b2);
            ops.add(product);
        }

        result = ops.get(0); // Finally, get the root one in the List, should be ONE left anyway

        return result;
    }

    /**
     * Checks a list of Operators to see if any has the ATTRIBUTE in its output Relation.
     *
     * If it does, then the Operator is extracted from the List and Returned,
     * If mulitple matches are found, only the first match is returned.
     *
     * @param oList the List of Operators to check for a match
     * @param attr the ATTRIBUTE to match
     * @return an Operator if a match is found, null otherwise
     */
    private Operator extractOperatorForAttribute(List<Operator> oList, Attribute attr){

        // go through the Operators
        Iterator<Operator> oIt = oList.iterator();
        while(oIt.hasNext()){

            Operator curOp = oIt.next();
            // If the Operator contains the attribute in its output Relation, remove and return it
            if (curOp.getOutput().getAttributes().contains(attr)){
                oIt.remove();
                return curOp;
            }
        }
        return null;
    }

    /**
     * Get the Set of ATTRIBUTEs that are needed based on the List PREDICATEs and the Operator
     *
     * @param predicates the PREDICATEs to check for ATTRIBUTEs
     * @param root the root Operator in the tree which may not have any additional ATTRIBUTEs
     * @return the Set of ATTRIBUTEs necessary still
     */
    private Set<Attribute> getNecessaryAttrs(List<Predicate> predicates, Operator root){

        // the Set of necessary ATTRIBUTEs
        Set<Attribute> attrsNeeded = new HashSet<>();

        // Iterate over the PREDICATEs
        Iterator<Predicate> predIt = predicates.iterator();
        while(predIt.hasNext()){

            // Add the ATTRIBUTEs of the PREDICATE
            Predicate currentPred = predIt.next();
            Attribute left = currentPred.getLeftAttribute();
            Attribute right = currentPred.getRightAttribute();

            attrsNeeded.add(left);
            if (right != null) attrsNeeded.add(right);
        }

        // If root is a PROJECT then add its ATTRIBUTEs in
        if (root instanceof Project) attrsNeeded.addAll(((Project) root).getAttributes());

        return attrsNeeded;
    }

    /**
     * Generate a List of all possible permutations of List of PREDICATEs
     * @param attrs the List of ATTRIBUTEs to permute
     * @return List of possible permutations of attrs
     */
    private List<List<Predicate>> generatePerm(List<Predicate> attrs) {

        // if there's only one element
        if (attrs.size() == 0) {
            List<List<Predicate>> result = new ArrayList<List<Predicate>>();
            result.add(new ArrayList<Predicate>());
            return result;
        }

        // there are multiple elements, remove the first
        Predicate first = attrs.remove(0);
        List<List<Predicate>> returnValue = new ArrayList<List<Predicate>>();
        // recursively call
        List<List<Predicate>> permutations = generatePerm(attrs);

        // iterate over the permutations
        for (List<Predicate> smallerPermutated : permutations) {
            for (int index=0; index <= smallerPermutated.size(); index++) {
                List<Predicate> temp = new ArrayList<Predicate>(smallerPermutated);
                temp.add(index, first);
                returnValue.add(temp);
            }
        }

        return returnValue;
    }

    public int getCost(Operator plan) {

        // Project
        if (plan instanceof Project) {
            // Add the cost of this operator
            EST.visit((Project) plan);
            totalCost += plan.getOutput().getTupleCount();
            // Go to the inner operator
            getCost(((Project) plan).getInput());
        }

        // Select
        else if (plan instanceof Select) {
            // Add the cost of this operator
            EST.visit((Select) plan);
            totalCost += plan.getOutput().getTupleCount();
            // Go to the inner operator
            getCost(((Select) plan).getInput());
        }

        // Product
        else if (plan instanceof Product) {
            // Add the cost of this operator
            EST.visit((Product) plan);
            totalCost += plan.getOutput().getTupleCount();
            // Go to the inner operators
            getCost(((Product) plan).getLeft());
            getCost(((Product) plan).getRight());
        }

        // Join
        else if (plan instanceof Join) {
            // Add the cost of this operator
            EST.visit((Join) plan);
            totalCost += plan.getOutput().getTupleCount();
            // Go to the inner operators
            getCost(((Join) plan).getLeft());
            getCost(((Join) plan).getRight());
        }

        // Scan
        else if (plan instanceof Scan) {
            // Add the cost of this operator
            EST.visit((Scan) plan);
            totalCost += plan.getOutput().getTupleCount();
        }

        return totalCost;
    }

    private Set<Attribute> allAttributes = new HashSet<>();
    private Set<Predicate> allPredicates = new HashSet<>();
    private Set<Scan> allScans = new HashSet<Scan>();

    public void visit(Scan op) { allScans.add(new Scan((NamedRelation)op.getRelation())); }
    public void visit(Project op) { allAttributes.addAll(op.getAttributes()); }
    public void visit(Product op) {}
    public void visit(Join op) {}
    public void visit(Select op) {
        allPredicates.add(op.getPredicate());
        allAttributes.add(op.getPredicate().getLeftAttribute());
        if(!op.getPredicate().equalsValue()) allAttributes.add(op.getPredicate().getRightAttribute());
    }
}