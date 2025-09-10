package com.example.dontcount

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dont_count.CameraActivity
import com.example.dont_count.R

class ExerciseListActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var titleTv: TextView

    private val exercisesByCategory = mapOf(
        "Legs" to listOf("Squats", "Lunges", "Calf Raises"),
        "Arms" to listOf("Push-ups", "Pull-ups", "Bicep Curls"),
        "Shoulders" to listOf("Shoulder Press", "Lateral Raise"),
        "Core" to listOf("Plank", "Sit-ups", "Russian Twist"),
        "Back" to listOf("Deadlift (demo)", "Superman")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_list)

        titleTv = findViewById(R.id.tvCategoryTitle)
        rv = findViewById(R.id.rvExercises)
        rv.layoutManager = LinearLayoutManager(this)

        val category = intent.getStringExtra("category") ?: "Legs"
        titleTv.text = category

        val list = exercisesByCategory[category] ?: listOf("Squats")
        val adapter = ExerciseAdapter(list) { exercise ->
            // Start CameraActivity and pass category + exercise
            val i = Intent(this, CameraActivity::class.java)
            i.putExtra("category", category)
            i.putExtra("exercise", exercise)
            startActivity(i)
        }
        rv.adapter = adapter
    }
}
