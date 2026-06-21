package com.personal.travelbuddy;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.ViewHolder> {

    private List<LocationAlarm> locations;
    private OnLocationClickListener listener;

    public interface OnLocationClickListener {
        void onLocationClick(LocationAlarm location);
        void onDeleteClick(LocationAlarm location);
    }

    public LocationAdapter(List<LocationAlarm> locations, OnLocationClickListener listener) {
        this.locations = locations;
        this.listener = listener;
    }

    public void setLocations(List<LocationAlarm> locations) {
        this.locations = locations;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_location, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationAlarm location = locations.get(position);
        holder.tvName.setText(location.name);
        holder.tvDetails.setText(String.format("Lat: %.4f, Lng: %.4f, Radius: %dm", 
                location.latitude, location.longitude, location.radius));

        holder.itemView.setOnClickListener(v -> listener.onLocationClick(location));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(location));
    }

    @Override
    public int getItemCount() {
        return locations != null ? locations.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDetails;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvLocationName);
            tvDetails = itemView.findViewById(R.id.tvLocationDetails);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
