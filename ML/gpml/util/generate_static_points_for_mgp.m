function [points]=generate_static_points_for_mgp(C)
	static_points_file=sprintf('static_points_for_%d_classes.mat',C);
	persistent static_points;
	if isempty(static_points) 
		if exist(static_points_file,'file')
			disp('loading....')
			load(static_points_file);
			assert( size(static_points,1)==C )
		else
			disp('generating....')
			sample_size=C*C*5000;
			static_points=mvnrnd(zeros(C,1),eye(C),sample_size)';
			save(static_points_file,'static_points');
		end
	end
	assert( ~isempty(static_points) ) 
	points=static_points;
