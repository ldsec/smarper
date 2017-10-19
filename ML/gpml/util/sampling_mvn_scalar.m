function [varargout] = sampling_mvn_scalar(lik_name,m,v,points,varargin)
	p=size(m,1);
	assert( size(points,1) == p )
	assert( size(v,1) == p )
	assert( size(v,2) == p )

	if nargin>4
		y=varargin(1);
		y=y{:};
		assert( length(y)==1)
		assert(y>=1 && y<=p)
	end

	%points is a p-by-sample_size matrix
	sample_size=size(points,2);
	L=chol(v,'lower');
	weighted_points=L*points+repmat(m,1,sample_size);

	switch lik_name
	case {'likSoftmax', 'loglikSoftmax'}
		max_p=max(weighted_points, [], 1);
		lp=log1p( sum( exp(weighted_points-repmat(max_p,p,1)), 1)-1 )+max_p;
		assert( size(lp,1)==1 ) %must be a function which maps R^p to R^1
		if isequal(lik_name,'loglikSoftmax')
			lp=exp(weighted_points(y,:)-lp); 
		end
	otherwise
		error('do not support');
	end
	f=sum(lp,2) ./ sample_size;
	if isequal(lik_name,'loglikSoftmax')
		f=log(f);
		assert(all(f<0) )
	end
	if nargout>1
		normal_p=lp./sample_size;
		dm=L'\(points * normal_p');
		dv=-0.5 * f * (v\eye(p));
		part=(L\eye(p));
		%dv=dv+0.5.*(part'*(points * diag(normal_p) * points')*part);
		dv=dv+0.5.*(part'*((points .* repmat(normal_p,p,1)) * points')*part);
		varargout={dm,dv};
	else
		varargout={f};
	end

