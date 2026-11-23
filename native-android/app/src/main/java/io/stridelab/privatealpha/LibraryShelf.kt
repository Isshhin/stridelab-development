package io.stridelab.privatealpha

import org.json.JSONObject
import org.json.JSONArray

/** Account-scoped local shelf; never stored in shared UI preferences. */
internal data class LibraryShelf(val favorites:Set<String> = emptySet(),val collections:Map<String,Set<String>> = emptyMap()) {
    fun encode(owner:String)=JSONObject().put("schemaVersion",1).put("user_id",owner).put("favorites",JSONArray(favorites.sorted())).put("collections",JSONObject(collections.mapValues { JSONArray(it.value.sorted()) }))
    fun change(id:String,collection:String?=null,remove:Boolean=false):LibraryShelf {
        RecordPolicy.identifier(id)
        if(collection==null) { if(id !in favorites && favorites.size>=200)throw PlanConstraint("Your favorites shelf is full.");return copy(favorites=if(id in favorites) favorites-id else favorites+id) }
        val name=collection.trim()
        if(name in setOf("All saved","Favorites"))throw PlanConstraint("Choose a collection name other than All saved or Favorites.")
        if(name.length !in 1..40 || name.any { it.isISOControl() })throw PlanConstraint("Use a collection name of 1–40 characters.")
        if(name !in collections && collections.size>=30)throw PlanConstraint("This shelf supports up to thirty collections.")
        val members=collections[name] ?: emptySet()
        if(!remove && id !in members && members.size>=200)throw PlanConstraint("This collection is full.")
        return copy(collections=collections+(name to if(remove) members-id else members+id))
    }
    companion object {
        fun decode(j:JSONObject,owner:String):LibraryShelf {
            require(j.getInt("schemaVersion")==1 && j.getString("user_id")==owner)
            val c=j.getJSONObject("collections")
            require(c.length()<=30)
            val shelf=LibraryShelf(j.getJSONArray("favorites").strings().toSet(),c.keys().asSequence().associateWith { c.getJSONArray(it).strings().toSet() })
            require(shelf.favorites.size<=200 && shelf.collections.values.all { it.size<=200 })
            return shelf
        }
    }
}
