function [varargout] = sampling_m_E(y, m, v, lik_name, type, varargin)
% This function approximates E( log p(y|x) ) where 
% expectation is wrt p(x) = N(x|m,v) with mean m and variance v.
% params are optional parameters required for approximation

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
  % vectorize all variables
  n=size(y,1);
  C_n=size(m,1);
  C=C_n/n;
  assert( C*n==C_n );
  assert( size(v,1)==C_n )
  assert( size(v,2)==C_n )

  switch type
  case {'precompute', 'precompute_lik'}
	  points=varargin(1);
	  points=points{:};
  case 'random'
	  sample_size=varargin(1);
	  sample_size=sample_size{:};
	  points=mvnrnd(zeros(C,1),eye(C),sample_size)';
  otherwise
	  error('unknown type')
  end

  sampling_idx_E=zeros(n,C);
  if isequal( type, 'precompute_lik')
	  for i=1:n
		  sampling_idx_E_lik(i,:)=i:n:C_n;
	  end
	  run_fun=@(i)( sampling_mvn_scalar(lik_name, m(sampling_idx_E_lik(i,:),:), v(sampling_idx_E_lik(i,:), sampling_idx_E_lik(i,:)) , points, y(i)) );
	  res=arrayfun(run_fun, 1:n,'UniformOutput',false);
	  lp=cell2mat(res);
	  varargout={lp};
  else
	  %persistent y_matrix
	  %persistent sampling_idx_E;
	  %if isempty(sampling_idx_E)
		  y_matrix=zeros(C,n);
		  for i=1:n
			  sampling_idx_E(i,:)=i:n:C_n;
			  y_matrix(y(i),i)=1;
		  end
	  %end
	  if nargout==1
		  run_fun=@(i)( m(sampling_idx_E(i,y(i)),:) - sampling_mvn_scalar(lik_name, m(sampling_idx_E(i,:),:), v(sampling_idx_E(i,:), sampling_idx_E(i,:)) , points) );
		  res=arrayfun(run_fun, 1:n,'UniformOutput',false);
		  lp=cell2mat(res);
		  varargout={lp};
	  else
		  run_fun2=@(i)( sampling_mvn_scalar(lik_name, m(sampling_idx_E(i,:),:), v(sampling_idx_E(i,:), sampling_idx_E(i,:)) , points) );
		  [dm dv]=arrayfun(run_fun2, 1:n,'UniformOutput',false);
		  gm=y_matrix-cell2mat(dm);
		  dv=cell2mat(dv);
		  gv=-reshape(dv,C,C,n);
		  varargout={gm,gv};
	  end
  end

