package com.example.dontcount

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dont_count.R

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var feedbackButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        feedbackButton = findViewById(R.id.feedbackButton)

        val categories = listOf(
            Category("Legs", R.drawable.running),
            Category("Arms", R.drawable.muscle),
            Category("Shoulders", R.drawable.exercise),
            Category("Core", R.drawable.bosuball),
            Category("Back", R.drawable.training)
        )

        val adapter = CategoryAdapter(categories) { category ->
            val intent = Intent(this, ExerciseListActivity::class.java)
            intent.putExtra("category", category.name)
            startActivity(intent)
        }

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        feedbackButton.setOnClickListener {
            startActivity(Intent(this, FeedbackActivity::class.java))
        }
    }
}
