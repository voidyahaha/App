package com.jeecommandcenter.data

object SyllabusCatalog {
    private val physics11 = listOf(
        "Units and Measurements", "Motion in a Straight Line", "Motion in a Plane",
        "Laws of Motion", "Work, Energy and Power", "System of Particles and Rotational Motion",
        "Gravitation", "Mechanical Properties of Solids", "Mechanical Properties of Fluids",
        "Thermal Properties of Matter", "Thermodynamics", "Kinetic Theory", "Oscillations", "Waves"
    )
    private val physics12 = listOf(
        "Electric Charges and Fields", "Electrostatic Potential and Capacitance",
        "Current Electricity", "Moving Charges and Magnetism", "Magnetism and Matter",
        "Electromagnetic Induction", "Alternating Current", "Electromagnetic Waves",
        "Ray Optics", "Wave Optics", "Dual Nature of Radiation and Matter",
        "Atoms", "Nuclei", "Semiconductor Electronics"
    )
    private val chemistry11 = listOf(
        "Some Basic Concepts of Chemistry", "Structure of Atom", "Classification of Elements",
        "Chemical Bonding", "Chemical Thermodynamics", "Equilibrium", "Redox Reactions",
        "Organic Chemistry: Basic Principles", "Hydrocarbons"
    )
    private val chemistry12 = listOf(
        "Solutions", "Electrochemistry", "Chemical Kinetics", "d- and f-Block Elements",
        "Coordination Compounds", "Haloalkanes and Haloarenes", "Alcohols, Phenols and Ethers",
        "Aldehydes, Ketones and Carboxylic Acids", "Amines", "Biomolecules"
    )
    private val mathematics11 = listOf(
        "Sets", "Relations and Functions", "Trigonometric Functions", "Complex Numbers",
        "Linear Inequalities", "Permutations and Combinations", "Binomial Theorem",
        "Sequences and Series", "Straight Lines", "Conic Sections", "Introduction to 3D Geometry",
        "Limits and Derivatives", "Statistics", "Probability"
    )
    private val mathematics12 = listOf(
        "Relations and Functions", "Inverse Trigonometric Functions", "Matrices",
        "Determinants", "Continuity and Differentiability", "Applications of Derivatives",
        "Integrals", "Applications of Integrals", "Differential Equations",
        "Vector Algebra", "Three-Dimensional Geometry", "Linear Programming", "Probability"
    )

    val chapters: List<Triple<String, Int, String>> = buildList {
        listOf("Physics" to (physics11 to physics12),
            "Chemistry" to (chemistry11 to chemistry12),
            "Mathematics" to (mathematics11 to mathematics12)).forEach { (subject, levels) ->
            levels.first.forEach { add(Triple(subject, 11, it)) }
            levels.second.forEach { add(Triple(subject, 12, it)) }
        }
    }
}
